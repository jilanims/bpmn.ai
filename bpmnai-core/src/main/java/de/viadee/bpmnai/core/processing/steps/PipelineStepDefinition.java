package de.viadee.bpmnai.core.processing.steps;

import java.util.ArrayList;
import java.util.List;

public class PipelineStepDefinition {

    private String id;
    private String name;
    private String className;
    private String description;
    private List<ParameterDefinition> parameters = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ParameterDefinition> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterDefinition> parameters) {
        this.parameters = parameters;
    }
}

