package de.viadee.ki.sparkimporter.processing.steps.userconfig;

import de.viadee.ki.sparkimporter.annotation.PreprocessingStepDescription;
import de.viadee.ki.sparkimporter.configuration.Configuration;
import de.viadee.ki.sparkimporter.configuration.preprocessing.PreprocessingConfiguration;
import de.viadee.ki.sparkimporter.configuration.preprocessing.VariableConfiguration;
import de.viadee.ki.sparkimporter.configuration.util.ConfigurationUtils;
import de.viadee.ki.sparkimporter.processing.interfaces.PreprocessingStepInterface;
import de.viadee.ki.sparkimporter.util.SparkImporterLogger;
import de.viadee.ki.sparkimporter.util.SparkImporterUtils;
import de.viadee.ki.sparkimporter.util.SparkImporterVariables;
import org.apache.spark.api.java.function.FilterFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@PreprocessingStepDescription(value = "If the configuration contains variable that should not be processed then they are filtered out during this step.")
public class VariableFilterStep implements PreprocessingStepInterface {
    @Override
    public Dataset<Row> runPreprocessingStep(Dataset<Row> dataSet, boolean writeStepResultIntoFile, String dataLevel, Map<String, Object> parameters) {

        List<String> variablesToFilter = new ArrayList<>();

        Configuration configuration = ConfigurationUtils.getInstance().getConfiguration();
        if(configuration != null) {
            PreprocessingConfiguration preprocessingConfiguration = configuration.getPreprocessingConfiguration();
            if(preprocessingConfiguration != null) {
                for(VariableConfiguration vc : preprocessingConfiguration.getVariableConfiguration()) {
                    if(!vc.isUseVariable()) {
                        variablesToFilter.add(vc.getVariableName());
                        SparkImporterLogger.getInstance().writeInfo("The variable '" + vc.getVariableName() + "' will be filtered out. Comment: " + vc.getComment());
                    }
                }
            }

        }

        //check if all variables that should be filtered actually exist, otherwise log a warning
        List<Row> existingVariablesRows = dataSet.select(SparkImporterVariables.VAR_PROCESS_INSTANCE_VARIABLE_NAME).distinct().collectAsList();
        List<String> existingVariables = existingVariablesRows
                .stream()
                .map(r -> r.getString(0)).collect(Collectors.toList());

        variablesToFilter
                .stream()
                .forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        if(!existingVariables.contains(s)) {
                            // log the fact that a variable that should be filtered does not exist
                            SparkImporterLogger.getInstance().writeWarn("The variable '" + s + "' is configured to be filtered, but does not exist in the data.");
                        }
                    }
                });

        dataSet = dataSet.filter((FilterFunction<Row>) row -> {
            // keep the row if the variable name column does not contain a value that should be filtered
            String variable = row.getAs(SparkImporterVariables.VAR_PROCESS_INSTANCE_VARIABLE_NAME);

            //TODO: cleanup
            boolean keep = !variablesToFilter.contains(variable);
            if(variable != null && variable.startsWith("_CORRELATION_ID_")) {
                keep = false;
            }

            return keep;
        });

        if(writeStepResultIntoFile) {
            SparkImporterUtils.getInstance().writeDatasetToCSV(dataSet, "variable_filter");
        }

        return dataSet;
    }
}
