package test_with_remote_apis.methods;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.dnd.*;
import com.slack.api.model.User;
import config.Constants;
import config.SlackTestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
public class dnd_Test {

    String botToken = System.getenv(Constants.SLACK_SDK_TEST_BOT_TOKEN);
    String userToken = System.getenv(Constants.SLACK_SDK_TEST_USER_TOKEN);

    static SlackTestConfig testConfig = SlackTestConfig.getInstance();
    static Slack slack = Slack.getInstance(testConfig.getConfig());

    @BeforeClass
    public static void setUp() throws Exception {
        SlackTestConfig.initializeRawJSONDataFiles("dnd.*");
    }

    @AfterClass
    public static void tearDown() throws InterruptedException {
        SlackTestConfig.awaitCompletion(testConfig);
    }

    @Test
    public void dnd() throws Exception {
        // Using async client to avoid an exception due to rate limited errors
        List<User> members = slack.methodsAsync().usersList(r -> r.token(botToken).presence(true)).get().getMembers();
        {
            String user = members.get(0).getId();
            DndInfoResponse response = slack.methods().dndInfo(r -> r.token(botToken).user(user));
            assertThat(response.getError(), is(nullValue()));
            assertThat(response.isOk(), is(true));
            assertThat(response.getNextDndStartTs(), is(notNullValue()));
        }

        {
            List<String> users = new ArrayList<>();
            for (User member : members) {
                users.add(member.getId());
            }
            DndTeamInfoResponse response = slack.methods().dndTeamInfo(r -> r.token(botToken).users(users));
            assertThat(response.getError(), is(nullValue()));
            assertThat(response.isOk(), is(true));
            assertThat(response.getUsers(), is(notNullValue()));
        }
    }

    @Test
    public void dndEndDnd() throws Exception {
        {
            DndEndDndResponse response = slack.methods().dndEndDnd(r -> r
                    //.token(token)
            );
            assertThat(response.getError(), is(notNullValue()));
            assertThat(response.isOk(), is(false));
        }
        {
            DndEndDndResponse response = slack.methods().dndEndDnd(r -> r
                    .token(userToken));
            assertThat(response.getError(), is(nullValue()));
            assertThat(response.isOk(), is(true));
        }
    }

    @Test
    public void dndEndSnooze() throws Exception {
        {
            DndEndSnoozeResponse response = slack.methods().dndEndSnooze(r -> r
                    //.token(token)
            );
            assertThat(response.getError(), is(notNullValue()));
            assertThat(response.isOk(), is(false));
        }
        {
            DndEndSnoozeResponse response = slack.methods().dndEndSnooze(r -> r.token(userToken));
            // Since Aug 2022, "snooze_not_active" is no longer returned for any cases
            // assertThat(response.getError(), is("snooze_not_active"));
            // assertThat(response.isOk(), is(false));
            assertThat(response.getError(), is(nullValue()));
        }

        {
            DndSetSnoozeResponse response = slack.methods().dndSetSnooze(r -> r
                    //.token(token)
                    .numMinutes(10));
            assertThat(response.getError(), is(notNullValue()));
            assertThat(response.isOk(), is(false));
        }
        {
            DndSetSnoozeResponse response = slack.methods().dndSetSnooze(r -> r
                    .token(userToken)
                    .numMinutes(10));
            assertThat(response.getError(), is(nullValue()));
            assertThat(response.isOk(), is(true));
        }
        {
            DndEndSnoozeResponse response = slack.methods().dndEndSnooze(r -> r
                    .token(userToken));
            assertThat(response.getError(), is(nullValue()));
            assertThat(response.isOk(), is(true));
        }
    }

}
