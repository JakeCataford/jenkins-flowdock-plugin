package com.flowdock.jenkins;

import com.flowdock.jenkins.exception.FlowdockException;
import hudson.Extension;
import hudson.Launcher;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class FlowdockNotifier extends Notifier {

    private final String flowToken;
    private final String notificationTags;

    private final Map<BuildResult, Boolean> notifyMap;
    private final Map<BuildResult, Boolean> chatMap;
    private final boolean notifySuccess;
    private final boolean notifyFailure;
    private final boolean notifyFixed;
    private final boolean notifyUnstable;
    private final boolean notifyAborted;
    private final boolean notifyNotBuilt;
    private final boolean chatSuccess;
    private final boolean chatFailure;
    private final boolean chatFixed;
    private final boolean chatUnstable;
    private final boolean chatAborted;
    private final boolean chatNotBuilt;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public FlowdockNotifier(String flowToken, String notificationTags,
        String notifySuccess, String notifyFailure, String notifyFixed, String notifyUnstable,
        String notifyAborted, String notifyNotBuilt,  String chatSuccess, String chatFailure, String chatFixed, String chatUnstable,
        String chatAborted, String chatNotBuilt) {
        this.flowToken = flowToken;
        this.notificationTags = notificationTags;

        this.notifySuccess = notifySuccess != null && notifySuccess.equals("true");
        this.notifyFailure = notifyFailure != null && notifyFailure.equals("true");
        this.notifyFixed = notifyFixed != null && notifyFixed.equals("true");
        this.notifyUnstable = notifyUnstable != null && notifyUnstable.equals("true");
        this.notifyAborted = notifyAborted != null && notifyAborted.equals("true");
        this.notifyNotBuilt = notifyNotBuilt != null && notifyNotBuilt.equals("true");

        this.chatSuccess = chatSuccess != null && chatSuccess.equals("true");
        this.chatFailure = chatFailure != null && chatFailure.equals("true");
        this.chatFixed = chatFixed != null && chatFixed.equals("true");
        this.chatUnstable = chatUnstable != null && chatUnstable.equals("true");
        this.chatAborted = chatAborted != null && chatAborted.equals("true");
        this.chatNotBuilt = chatNotBuilt != null && chatNotBuilt.equals("true");

        // set notification map
        this.notifyMap = new HashMap<BuildResult, Boolean>();
        this.notifyMap.put(BuildResult.SUCCESS, this.notifySuccess);
        this.notifyMap.put(BuildResult.FAILURE, this.notifyFailure);
        this.notifyMap.put(BuildResult.FIXED, this.notifyFixed);
        this.notifyMap.put(BuildResult.UNSTABLE, this.notifyUnstable);
        this.notifyMap.put(BuildResult.ABORTED, this.notifyAborted);
        this.notifyMap.put(BuildResult.NOT_BUILT, this.notifyNotBuilt);

        // set chat map
        this.chatMap = new HashMap<BuildResult, Boolean>();
        this.chatMap.put(BuildResult.SUCCESS, this.chatSuccess);
        this.chatMap.put(BuildResult.FAILURE, this.chatFailure);
        this.chatMap.put(BuildResult.FIXED, this.chatFixed);
        this.chatMap.put(BuildResult.UNSTABLE, this.chatUnstable);
        this.chatMap.put(BuildResult.ABORTED, this.chatAborted);
        this.chatMap.put(BuildResult.NOT_BUILT, this.chatNotBuilt);
    }

    public String getFlowToken() {
        return flowToken;
    }

    public String getNotificationTags() {
        return notificationTags;
    }

    public boolean getNotifySuccess() {
        return notifySuccess;
    }
    public boolean getNotifyFailure() {
        return notifyFailure;
    }
    public boolean getNotifyFixed() {
        return notifyFixed;
    }
    public boolean getNotifyUnstable() {
        return notifyUnstable;
    }
    public boolean getNotifyAborted() {
        return notifyAborted;
    }
    public boolean getNotifyNotBuilt() {
        return notifyNotBuilt;
    }

    public boolean getchatSuccess() {
        return chatSuccess;
    }
    public boolean getchatFailure() {
        return chatFailure;
    }
    public boolean getchatFixed() {
        return chatFixed;
    }
    public boolean getchatUnstable() {
        return chatUnstable;
    }
    public boolean getchatAborted() {
        return chatAborted;
    }
    public boolean getchatNotBuilt() {
        return chatNotBuilt;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        BuildResult buildResult = BuildResult.fromBuild(build);

        try {
            if(shouldNotify(buildResult)) {
                notifyFlowdock(build, buildResult, listener);
            } else {
                listener.getLogger().println("No Flowdock notification configured for build status: " + buildResult.toString());
            }
        } catch (Exception e) {
            listener.getLogger().println("Couldn't get build result settings for sidebar. Continuing as if nothing ever happened....");
        }

        try {
            if(shouldChat(buildResult)) {
                chatFlowdock(build, buildResult, listener);
            } else {
                listener.getLogger().println("No Flowdock chat message configured for build status: " + buildResult.toString());
            }
        } catch (Exception e) {
            listener.getLogger().println("Couldn't get build result settings for chat. Continuing as if nothing ever happened....");
        }

        return true;
    }

    public boolean shouldNotify(BuildResult buildResult) {
        return notifyMap.get(buildResult);
    }

    public boolean shouldChat(BuildResult buildResult) {
        return chatMap.get(buildResult);
    }

    protected void notifyFlowdock(AbstractBuild build, BuildResult buildResult, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        try {
            FlowdockAPI api = new FlowdockAPI(getDescriptor().apiUrl(), flowToken);
            TeamInboxMessage msg = TeamInboxMessage.fromBuild(build, buildResult);
            msg.setTags(notificationTags);
            api.pushTeamInboxMessage(msg);
            listener.getLogger().println("Flowdock: Team Inbox notification sent successfully");
        } catch(FlowdockException ex) {
            logger.println("Flowdock: failed to send notification");
            logger.println("Flowdock: " + ex.getMessage());
        }
    }

    protected void chatFlowdock(AbstractBuild build, BuildResult buildResult, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        try {
            FlowdockAPI api = new FlowdockAPI(getDescriptor().apiUrl(), flowToken);
            ChatMessage msg = ChatMessage.fromBuild(build, buildResult);
            msg.setTags(notificationTags);
            api.pushChatMessage(msg);
            listener.getLogger().println("Flowdock: Chat notification sent successfully");
        } catch(FlowdockException ex) {
            logger.println("Flowdock: failed to send chat notification");
            logger.println("Flowdock: " + ex.getMessage());
        }

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String apiUrl = "https://api.flowdock.com";

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Flowdock notification";
        }

        public FormValidation doTestConnection(@QueryParameter("flowToken") final String flowToken,
            @QueryParameter("notificationTags") final String notificationTags) {
            try {
                FlowdockAPI api = new FlowdockAPI(apiUrl(), flowToken);
                ChatMessage testMsg = new ChatMessage();
                testMsg.setTags(notificationTags);
                testMsg.setContent("Your plugin is ready!");
                api.pushChatMessage(testMsg);
                return FormValidation.ok("Success! Flowdock plugin can send notifications to your flow.");
            } catch(FlowdockException ex) {
                return FormValidation.error(ex.getMessage());
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            apiUrl = formData.getString("apiUrl");
            save();
            return super.configure(req, formData);
        }

        public String apiUrl() {
            return apiUrl;
        }
    }
}
