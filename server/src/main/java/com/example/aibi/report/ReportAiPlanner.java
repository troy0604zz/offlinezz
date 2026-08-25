package com.example.aibi.report;

import com.example.aibi.query.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ReportAiPlanner {
    private final LlmProvider llm;
    private final ObjectMapper mapper;

    public ReportAiPlanner(LlmProvider llm,ObjectMapper mapper){this.llm=llm;this.mapper=mapper;}

    public List<SectionPlan> plan(String domain,String title,String request,List<Map<String,Object>> metrics,
                                  List<Map<String,Object>> relations,List<String> standardQuestions){
        String system="""
                REPORT_PLAN
                你是企业 BI 报告规划器。必须根据用户本次要求动态规划 1 到 4 个可执行的数据查询，禁止套用固定销售模板。
                每个 question 必须是能由一条只读 SELECT 回答的具体自然语言问题，不要要求模型自己编造数据。
                如果“已发布标准问题”能支撑用户要求，question 必须原样复用其中的问题，以便执行管理员审核过的 SQL。
                只返回 JSON：{"sections":[{"title":"章节标题","question":"具体数据问题"}]}。
                """;
        String user="数据域="+domain+"\n报告标题="+title+"\n可用指标="+safeJson(metrics)+"\n可用关系="+safeJson(relations)+
                "\n已发布标准问题="+safeJson(standardQuestions)+
                "\nREPORT_REQUEST_BEGIN\n"+request+"\nREPORT_REQUEST_END";
        try{
            JsonNode root=mapper.readTree(clean(llm.completeJson(system,user)));
            List<SectionPlan> result=new ArrayList<>();
            for(JsonNode node:root.path("sections")){
                String sectionTitle=node.path("title").asText().trim();
                String question=node.path("question").asText().trim();
                if(!sectionTitle.isBlank()&&!question.isBlank()) result.add(new SectionPlan(sectionTitle,question));
                if(result.size()==4) break;
            }
            if(!result.isEmpty()) return result;
        }catch(Exception ignored){ }
        return List.of(new SectionPlan(title,request));
    }

    public Narrative narrative(String domain,String request,List<Map<String,Object>> evidence){
        String system="""
                REPORT_NARRATIVE
                你是谨慎的企业数据分析师。只能依据提供的真实查询摘要撰写执行摘要和行动建议；不得补造数字或因果。
                只返回 JSON：{"executiveSummary":"...","recommendations":["...","..."]}。
                """;
        String user="数据域="+domain+"\nREPORT_REQUEST_BEGIN\n"+request+"\nREPORT_REQUEST_END\n真实查询证据="+safeJson(evidence);
        try{
            JsonNode root=mapper.readTree(clean(llm.completeJson(system,user)));
            String summary=root.path("executiveSummary").asText().trim();
            List<String> recommendations=new ArrayList<>();
            for(JsonNode item:root.path("recommendations")) if(!item.asText().isBlank()) recommendations.add(item.asText());
            if(!summary.isBlank()) return new Narrative(summary,recommendations);
        }catch(Exception ignored){ }
        return new Narrative("报告已根据本次分析要求实时执行 "+evidence.size()+" 个受控查询；请结合各章节结果、SQL 与口径说明进行业务判断。",
                List.of("复核异常值对应的业务对象和时间范围","发布前由数据域负责人确认指标口径"));
    }

    private String safeJson(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){return "[]";}}
    private String clean(String value){return value==null?"{}":value.replaceFirst("(?s)^\\s*```(?:json)?\\s*","").replaceFirst("(?s)\\s*```\\s*$","").trim();}
    public record SectionPlan(String title,String question){}
    public record Narrative(String executiveSummary,List<String> recommendations){}
}
