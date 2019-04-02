package com.getbase.jenkins.plugins.metrics.history.influxdb.generators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.synopsys.arc.jenkins.plugins.ownership.jobs.JobOwnerJobAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.metrics.impl.TimeInQueueAction;
import org.influxdb.dto.Point;

public class JenkinsBasePointGenerator implements PointGenerator {
    private static final String MEASUREMENT_NAME = "jenkins_build_data";
    private static final String JOB_NAME = "job_name";
    private static final String BUILD_NUMBER = "build_number";
    private static final String BUILD_RESULT = "build_result";
    private static final String BUILD_RESULT_INT = "build_result_int";
    private static final String BUILD_TIMESTAMP = "build_timestamp";
    private static final String BUILD_DURATION = "build_duration";
    private static final String QUEUING_DURATION = "queuing_duration";
    private static final String TOTAL_DURATION = "total_duration";
    private static final String BUILD_STATUS_MESSAGE = "build_status_message";
    private static final String JOB_OWNER = "job_owner";
    private static final String JOB_SCORE = "job_score";
    private static final String BUILD_URL = "build_url";
    private static final String JOB_URL = "job_url";

    private final Run<?, ?> build;

    public JenkinsBasePointGenerator(Run<?, ?> build) {
        this.build = build;
    }

    public static Map<String, Object> getBuildParameters(Run build) {
        List<ParametersAction> actions = build.getActions(ParametersAction.class);
        if (actions != null) {
            Map<String, Object> parametersMap = new HashMap<>();
            for (ParametersAction action : actions) {
                List<ParameterValue> parameters = action.getParameters();
                if (parameters != null) {
                    for (ParameterValue parameter : parameters) {
                        String name = parameter.getName();
                        Object value = parameter.getValue();
                        parametersMap.put(name, value);
                    }
                }
            }
            return parametersMap;
        }
        return null;
    }

    public Point[] generate() {
        // Build is not finished when running with pipelines. Duration must be calculated manually
        long startTime = build.getTimeInMillis();
        long currTime = System.currentTimeMillis();
        long dt = currTime - startTime;
        long duration = build.getDuration() == 0 ? dt : build.getDuration();

        TimeInQueueAction action = build.getAction(TimeInQueueAction.class);
        String owner = build.getParent().getAction(JobOwnerJobAction.class).getOwnership().getPrimaryOwnerEmail();
        int score = build.getParent().getBuildHealth().getScore();
        final Result result = build.getResult();
        final String resultStr = result != null ? result.toString() : "UNKNOWN";
        final Integer resultInt = resultStr.equals("SUCCESS") ? 1 : 0;
        final String jobAbsoluteURL = build.getParent().getAbsoluteUrl();
        final String buildAbsoluteURL = jobAbsoluteURL + build.getNumber();
        final Map<String, Object> fields = getBuildParameters(build);

        Point.Builder point = Point
                .measurement(MEASUREMENT_NAME)
                .addField(JOB_NAME, build.getParent().getFullName())
                .tag(JOB_NAME, build.getParent().getFullName())
                .addField(JOB_URL, jobAbsoluteURL)
                .tag(JOB_URL, jobAbsoluteURL)
                .addField(JOB_OWNER, owner)
                .tag(JOB_OWNER, owner)
                .addField(BUILD_NUMBER, build.getNumber())
                .addField(BUILD_RESULT, resultStr)
                .addField(BUILD_RESULT_INT, resultInt)
                .addField(JOB_SCORE, score)
                .addField(BUILD_URL, buildAbsoluteURL)
                .addField(BUILD_TIMESTAMP, build.getTimeInMillis())
                .addField(BUILD_DURATION, duration)
                .addField(QUEUING_DURATION, action.getQueuingDurationMillis())
                .addField(TOTAL_DURATION, duration + action.getQueuingDurationMillis())
                .addField(BUILD_STATUS_MESSAGE, build.getBuildStatusSummary().message)
                .fields(fields);

        return new Point[] {point.build()};
    }


}
