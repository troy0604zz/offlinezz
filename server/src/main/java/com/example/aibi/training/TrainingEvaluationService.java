package com.example.aibi.training;

import com.example.aibi.query.AskRequest;
import com.example.aibi.query.QueryAnswer;
import com.example.aibi.query.QueryOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TrainingEvaluationService {
    private final TrainingService training;
    private final QueryOrchestrator orchestrator;
    private final ObjectMapper mapper;

    public TrainingEvaluationService(TrainingService training, QueryOrchestrator orchestrator, ObjectMapper mapper) {
        this.training=training; this.orchestrator=orchestrator; this.mapper=mapper;
    }

    public Map<String,Object> run(long id) {
        Map<String,Object> golden=training.golden(id);
        String question=String.valueOf(golden.get("question"));
        String domain=String.valueOf(golden.get("domain"));
        QueryAnswer answer=orchestrator.ask(new AskRequest(question,domain));
        double score=score(golden,answer);
        String status=score>=0.999?"PASSED":"FAILED";
        String detail="SQL="+answer.sql()+"；返回 "+answer.rows().size()+" 行";
        training.saveEvaluation(id,status,score,detail);
        return Map.of("id",id,"status",status,"score",score,"answer",answer);
    }

    private double score(Map<String,Object> golden,QueryAnswer answer) {
        try {
            Object expectedJson=golden.get("expected_result_json");
            if(expectedJson!=null && !String.valueOf(expectedJson).isBlank()) {
                JsonNode expected=mapper.readTree(String.valueOf(expectedJson));
                JsonNode actual=mapper.valueToTree(answer.rows());
                return equivalent(expected,actual)?1:0;
            }
            Object expectedSql=golden.get("expected_sql");
            if(expectedSql!=null && !String.valueOf(expectedSql).isBlank())
                return normalize(String.valueOf(expectedSql)).equals(normalize(answer.sql()))?1:0;
            return "COMPLETED".equals(answer.status())?1:0;
        } catch(Exception ex) { return 0; }
    }

    private String normalize(String sql) { return sql.replaceAll("\\s+"," ").trim().replaceAll(";$","").toLowerCase(); }

    private boolean equivalent(JsonNode expected,JsonNode actual) {
        if(expected==null||actual==null) return expected==actual;
        if(expected.isNumber()&&actual.isNumber()) return expected.decimalValue().compareTo(actual.decimalValue())==0;
        if(expected.isArray()&&actual.isArray()) {
            if(expected.size()!=actual.size()) return false;
            for(int i=0;i<expected.size();i++) if(!equivalent(expected.get(i),actual.get(i))) return false;
            return true;
        }
        if(expected.isObject()&&actual.isObject()) {
            if(expected.size()!=actual.size()) return false;
            var names=expected.fieldNames();
            while(names.hasNext()) { String name=names.next(); if(!actual.has(name)||!equivalent(expected.get(name),actual.get(name))) return false; }
            return true;
        }
        return expected.equals(actual);
    }
}
