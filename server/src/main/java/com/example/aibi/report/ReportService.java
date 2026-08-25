package com.example.aibi.report;

import com.example.aibi.auth.CurrentUserProvider;
import com.example.aibi.common.BusinessException;
import com.example.aibi.common.DatabaseRows;
import com.example.aibi.domain.DomainAccessService;
import com.example.aibi.query.AskRequest;
import com.example.aibi.query.QueryAnswer;
import com.example.aibi.query.QueryOrchestrator;
import com.example.aibi.semantic.SemanticService;
import com.example.aibi.training.SqlExampleMatch;
import com.example.aibi.training.TrainingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {
    private final QueryOrchestrator orchestrator;
    private final ReportAiPlanner planner;
    private final SemanticService semantic;
    private final TrainingService training;
    private final DomainAccessService access;
    private final CurrentUserProvider currentUser;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ReportService(QueryOrchestrator orchestrator,ReportAiPlanner planner,SemanticService semantic,TrainingService training,
                         DomainAccessService access,CurrentUserProvider currentUser,JdbcClient jdbc,ObjectMapper mapper){
        this.orchestrator=orchestrator;this.planner=planner;this.semantic=semantic;this.training=training;this.access=access;
        this.currentUser=currentUser;this.jdbc=jdbc;this.mapper=mapper;
    }

    public Map<String,Object> generate(ReportRequest request){
        String domain=access.requireReport(request.domainOrDefault());
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO report_job(id,title,request_text,status,domain,created_by) VALUES(?,?,?,'GENERATING',?,?)")
                .params(id,request.title(),request.request(),domain,currentUser.username()).update();
        try{
            List<SqlExampleMatch> standardExamples=training.relevantExamples(domain,
                    request.title()+" "+request.request(),8);
            List<ReportAiPlanner.SectionPlan> plan=planner.plan(domain,request.title(),request.request(),
                    semantic.metricsForQuery(domain),semantic.relationsForQuery(domain),
                    standardExamples.stream().map(SqlExampleMatch::question).toList());
            List<Map<String,Object>> sections=new ArrayList<>();
            List<Map<String,Object>> evidence=new ArrayList<>();
            List<String> warnings=new ArrayList<>();
            RuntimeException lastFailure=null;
            for(ReportAiPlanner.SectionPlan sectionPlan:plan){
                ReportAiPlanner.SectionPlan executablePlan=canonicalize(domain,sectionPlan);
                try {
                    QueryAnswer answer=orchestrator.askForReport(new AskRequest(executablePlan.question(),domain));
                    sections.add(Map.of("title",executablePlan.title(),"question",executablePlan.question(),"query",answer));
                    Map<String,Object> item=new LinkedHashMap<>();
                    item.put("title",executablePlan.title());item.put("question",executablePlan.question());item.put("answer",answer.answer());
                    item.put("rows",answer.rows().stream().limit(10).toList());item.put("sql",answer.sql());
                    evidence.add(item);
                } catch(RuntimeException sectionFailure) {
                    lastFailure=sectionFailure;
                    String warning=executablePlan.title()+"："+rootMessage(sectionFailure);
                    warnings.add(warning);
                    sections.add(Map.of("title",executablePlan.title(),"question",executablePlan.question(),"error",rootMessage(sectionFailure)));
                }
            }
            if(evidence.isEmpty()&&!standardExamples.isEmpty()){
                sections.clear();
                warnings.clear();
                warnings.add("模型动态规划的临时 SQL 未通过执行校验，已自动改用本数据域中管理员发布的标准 SQL。");
                int target=Math.min(3,Math.max(1,plan.size()));
                for(SqlExampleMatch standard:standardExamples){
                    if(standard.score()<0.03||evidence.size()>=target) break;
                    ReportAiPlanner.SectionPlan fallback=new ReportAiPlanner.SectionPlan(standard.question(),standard.question());
                    try{
                        QueryAnswer answer=orchestrator.askForReport(new AskRequest(fallback.question(),domain));
                        sections.add(Map.of("title",fallback.title(),"question",fallback.question(),"query",answer));
                        Map<String,Object> item=new LinkedHashMap<>();
                        item.put("title",fallback.title());item.put("question",fallback.question());item.put("answer",answer.answer());
                        item.put("rows",answer.rows().stream().limit(10).toList());item.put("sql",answer.sql());
                        evidence.add(item);
                    }catch(RuntimeException fallbackFailure){
                        lastFailure=fallbackFailure;
                    }
                }
            }
            if(evidence.isEmpty()&&lastFailure!=null) throw lastFailure;
            ReportAiPlanner.Narrative narrative=planner.narrative(domain,request.request(),evidence);
            Map<String,Object> report=new LinkedHashMap<>();
            report.put("id",id);report.put("domain",domain);report.put("title",request.title());report.put("request",request.request());
            report.put("executiveSummary",narrative.executiveSummary());report.put("sections",sections);
            report.put("recommendations",narrative.recommendations());report.put("generatedAt",Instant.now().toString());
            report.put("warnings",warnings);
            report.put("generatedBy",currentUser.username());
            jdbc.sql("UPDATE report_job SET status='READY',content_json=?,error_message=NULL WHERE id=?")
                    .params(mapper.writeValueAsString(report),id).update();
            return report;
        }catch(Exception ex){
            jdbc.sql("UPDATE report_job SET status='FAILED',error_message=? WHERE id=?")
                    .params(rootMessage(ex),id).update();
            if(ex instanceof BusinessException business) throw business;
            throw new BusinessException("REPORT_GENERATION_FAILED","报告生成失败："+rootMessage(ex),HttpStatus.BAD_REQUEST);
        }
    }

    public List<Map<String,Object>> list(String rawDomain){
        String domain=access.requireReport(rawDomain);
        boolean administrator=isAiAdmin();
        List<Map<String,Object>> reports=DatabaseRows.normalize(jdbc.sql("SELECT id,domain,title,request_text,status,error_message,created_by,created_at FROM report_job WHERE domain=? ORDER BY created_at DESC")
                .param(domain).query().listOfRows());
        reports.forEach(report -> report.put("can_delete", administrator || currentUser.username().equals(String.valueOf(report.get("created_by")))));
        return reports;
    }

    public Map<String,Object> detail(String id){
        List<Map<String,Object>> matches=jdbc.sql("SELECT id,domain,title,request_text,status,content_json,error_message,created_by,created_at FROM report_job WHERE id=?")
                .param(id).query().listOfRows();
        if(matches.isEmpty()) throw new BusinessException("REPORT_NOT_FOUND","报告不存在",HttpStatus.NOT_FOUND);
        Map<String,Object> row=DatabaseRows.normalize(matches.get(0));
        access.requireReport(String.valueOf(row.get("domain")));
        Object content=row.get("content_json");
        if(content==null||String.valueOf(content).isBlank()) return row;
        try{return mapper.readValue(String.valueOf(content),new TypeReference<>(){});}
        catch(Exception ex){throw new IllegalStateException("报告历史内容解析失败",ex);}
    }

    @Transactional
    public Map<String,Object> delete(String id){
        List<Map<String,Object>> matches=jdbc.sql("SELECT id,domain,title,status,created_by FROM report_job WHERE id=?")
                .param(id).query().listOfRows();
        if(matches.isEmpty()) throw new BusinessException("REPORT_NOT_FOUND","报告不存在",HttpStatus.NOT_FOUND);
        Map<String,Object> report=DatabaseRows.normalize(matches.get(0));
        String domain=access.requireReport(String.valueOf(report.get("domain")));
        if("GENERATING".equals(String.valueOf(report.get("status")))){
            throw new BusinessException("REPORT_IS_GENERATING","报告正在生成，暂时不能删除",HttpStatus.CONFLICT);
        }
        boolean owner=currentUser.username().equals(String.valueOf(report.get("created_by")));
        if(!owner&&!isAiAdmin()){
            throw new BusinessException("REPORT_DELETE_FORBIDDEN","只能删除自己创建的报告",HttpStatus.FORBIDDEN);
        }
        try{
            jdbc.sql("INSERT INTO audit_event(trace_id,event_type,actor,resource_type,resource_id,detail) VALUES(?,'REPORT_DELETED',?,'REPORT',?,?)")
                    .params(id,currentUser.username(),id,mapper.writeValueAsString(Map.of(
                            "domain",domain,"title",String.valueOf(report.get("title")),"status",String.valueOf(report.get("status")))))
                    .update();
        }catch(Exception ex){
            throw new IllegalStateException("报告删除审计记录保存失败",ex);
        }
        jdbc.sql("DELETE FROM report_job WHERE id=?").param(id).update();
        return Map.of("deleted",true,"id",id);
    }

    private boolean isAiAdmin(){
        if("system".equals(currentUser.username())) return true;
        Integer count=jdbc.sql("SELECT COUNT(*) FROM app_user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='AI_ADMIN'")
                .param(currentUser.userId()).query(Integer.class).single();
        return count!=null&&count>0;
    }

    private ReportAiPlanner.SectionPlan canonicalize(String domain,ReportAiPlanner.SectionPlan planned){
        List<SqlExampleMatch> matches=training.relevantExamples(domain,planned.question(),1);
        if(matches.isEmpty()||matches.get(0).score()<0.18) return planned;
        return new ReportAiPlanner.SectionPlan(planned.title(),matches.get(0).question());
    }

    private String rootMessage(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage();}
}
