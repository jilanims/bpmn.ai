package de.viadee.bpmnai.core.runner.impl;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import de.viadee.bpmnai.core.processing.steps.dataprocessing.*;
import de.viadee.bpmnai.core.processing.steps.importing.InitialCleanupStep;
import de.viadee.bpmnai.core.util.BpmnaiUtils;
import de.viadee.bpmnai.core.exceptions.FaultyConfigurationException;
import de.viadee.bpmnai.core.processing.steps.PipelineStep;
import de.viadee.bpmnai.core.processing.steps.output.WriteToDiscStep;
import de.viadee.bpmnai.core.runner.SparkRunner;
import de.viadee.bpmnai.core.runner.config.SparkRunnerConfig;
import de.viadee.bpmnai.core.util.BpmnaiVariables;
import de.viadee.bpmnai.core.util.arguments.CSVImportAndProcessingArguments;
import de.viadee.bpmnai.core.util.logging.BpmnaiLogger;
import org.apache.commons.io.FileUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CSVImportAndProcessingRunner extends SparkRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CSVImportAndProcessingRunner.class);

    public CSVImportAndProcessingRunner() { super(); }

    public CSVImportAndProcessingRunner(SparkRunnerConfig config) {
        super(config);
    }

    @Override
    protected void initialize(String[] arguments) {
        CSVImportAndProcessingArguments csvImportAndProcessingArguments = CSVImportAndProcessingArguments.getInstance();

        // instantiate JCommander
        // Use JCommander for flexible usage of Parameters
        final JCommander jCommander = JCommander.newBuilder().addObject(CSVImportAndProcessingArguments.getInstance()).build();
        try {
            jCommander.parse(arguments);
        } catch (final ParameterException e) {
            LOG.error("Parsing of parameters failed. Error message: " + e.getMessage());
            jCommander.usage();
            System.exit(1);
        }

        //parse arguments to create SparkRunnerConfig
        csvImportAndProcessingArguments.createOrUpdateSparkRunnerConfig(this.sparkRunnerConfig);

        if(this.sparkRunnerConfig.isDevProcessStateColumnWorkaroundEnabled() && sparkRunnerConfig.getDataLevel().equals(BpmnaiVariables.DATA_LEVEL_ACTIVITY)) {
            try {
                throw new FaultyConfigurationException("Process state workaround option cannot be used with activity data level.");
            } catch (FaultyConfigurationException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        // Delete destination files, required to avoid exception during runtime
        if(this.sparkRunnerConfig.getSaveMode().equals(SaveMode.Overwrite)) {
            FileUtils.deleteQuietly(new File(this.sparkRunnerConfig.getTargetFolder()));
        }

        BpmnaiLogger.getInstance().writeInfo("Starting CSV import and processing");
        BpmnaiLogger.getInstance().writeInfo("Importing CSV file: " + this.sparkRunnerConfig.getSourceFolder());
    }

    @Override
    protected List<PipelineStep> buildDefaultPipeline() {
        List<PipelineStep> pipelineSteps = new ArrayList<>();

        pipelineSteps.add(new PipelineStep(new DataFilterStep(), ""));
        pipelineSteps.add(new PipelineStep(new ColumnRemoveStep(), "DataFilterStep"));
        pipelineSteps.add(new PipelineStep(new ReduceColumnsStep(), "ColumnRemoveStep"));
        pipelineSteps.add(new PipelineStep(new DetermineProcessVariablesStep(), "ReduceColumnsStep"));
        pipelineSteps.add(new PipelineStep(new AddVariableColumnsStep(), "DetermineProcessVariablesStep"));
        pipelineSteps.add(new PipelineStep(new AggregateProcessInstancesStep(), "AddVariableColumnsStep"));
        pipelineSteps.add(new PipelineStep(new CreateColumnsFromJsonStep(), "AggregateProcessInstancesStep"));
        pipelineSteps.add(new PipelineStep(new AddReducedColumnsToDatasetStep(), "CreateColumnsFromJsonStep"));
        pipelineSteps.add(new PipelineStep(new ColumnHashStep(), "AddReducedColumnsToDatasetStep"));
        pipelineSteps.add(new PipelineStep(new TypeCastStep(), "ColumnHashStep"));
        pipelineSteps.add(new PipelineStep(new WriteToDiscStep(), "TypeCastStep"));

        return pipelineSteps;
    }

    @Override
    protected Dataset<Row> loadInitialDataset() {

        //Load source CSV file
        Dataset<Row> dataset = sparkSession.read()
                .option("inferSchema", "true")
                .option("delimiter", this.sparkRunnerConfig.getDelimiter())
                .option("header", "true")
                .option("ignoreLeadingWhiteSpace", "false")
                .option("ignoreTrailingWhiteSpace", "false")
                .csv(this.sparkRunnerConfig.getSourceFolder());

        // write imported CSV structure to file for debugging
        if (this.sparkRunnerConfig.isWriteStepResultsIntoFile()) {
            BpmnaiUtils.getInstance().writeDatasetToCSV(dataset, "import_result", this.sparkRunnerConfig);
        }

        InitialCleanupStep initialCleanupStep = new InitialCleanupStep();
        dataset = initialCleanupStep.runPreprocessingStep(dataset, null, this.sparkRunnerConfig);

        //for CSV inputs all rows are handled as variableUpdate rows
        dataset = dataset.withColumn(BpmnaiVariables.VAR_DATA_SOURCE, functions.lit(BpmnaiVariables.EVENT_VARIABLE_UPDATE));

        return dataset;
    }
}
