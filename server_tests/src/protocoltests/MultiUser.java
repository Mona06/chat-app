package protocoltests;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import protocoltests.protocol.messages.*;
import protocoltests.protocol.utils.Utils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Properties;

import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Multi-user")
class MultiUser {

    private final static Properties PROPS = new Properties();

    private Socket socketUser1, socketUser2;
    private BufferedReader inUser1, inUser2;
    private PrintWriter outUser1, outUser2;
    private static String host;
    private static int port;
    private static int pingTimeMsDeltaAllowed;

    @BeforeAll
    static void setupAll() throws IOException {
        InputStream in = MultiUser.class.getResourceAsStream("testconfig.properties");
        PROPS.load(in);
        host = PROPS.getProperty("host");
        port = Integer.parseInt(PROPS.getProperty("port"));
        pingTimeMsDeltaAllowed = Integer.parseInt(PROPS.getProperty("ping_time_ms_delta_allowed"));
        assertNotNull(in);
        in.close();
    }

    @BeforeEach
    void setup() throws IOException {
        socketUser1 = new Socket(host, port);
        inUser1 = new BufferedReader(new InputStreamReader(socketUser1.getInputStream()));
        outUser1 = new PrintWriter(socketUser1.getOutputStream(), true);

        socketUser2 = new Socket(host, port);
        inUser2 = new BufferedReader(new InputStreamReader(socketUser2.getInputStream()));
        outUser2 = new PrintWriter(socketUser2.getOutputStream(), true);
    }

    @AfterEach
    void cleanup() throws IOException, InterruptedException {
        socketUser1.close();
        socketUser2.close();

        // Give server time to process disconnects
        Thread.sleep(100);
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class A_ClientList_is_received_by_requested_client {
        @Test
        @Tag("RQ-U200")
        void when_a_user_requests_a_list_of_clients() throws IOException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            // Connect user1
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            // Connect user2
            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");

            receiveLineWithTimeout(inUser1, "JOINED expected");

            outUser1.println(Utils.objectToMessage(new ClientListReq()));
            outUser1.flush();

            // Verify client list response
            String response = receiveLineWithTimeout(inUser1, "CL_RESP expected");
            ClientListResp clResp = Utils.messageToObject(response);
            assertEquals("OK", clResp.status());
            assertNotNull(clResp.users());
            assertEquals(2, clResp.users().size());
            assertTrue(clResp.users().contains("user1"));
            assertTrue(clResp.users().contains("user2"));
        }

        @Test
        @Tag("RQ-U200")
        void when_only_one_user_is_connected() throws IOException {
            receiveLineWithTimeout(inUser1, "Initial message expected");

            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            outUser1.println(Utils.objectToMessage(new ClientListReq()));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "CL_RESP expected");
            ClientListResp clResp = Utils.messageToObject(response);
            assertEquals("OK", clResp.status());
            assertEquals(1, clResp.users().size());
            assertTrue(clResp.users().contains("user1"));
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Private_message_succeeds {

        @Test
        @Tag("RQ-U201")
        void when_sending_valid_message_to_connected_user() throws JsonProcessingException {
            // Setup: consume HI messages
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            // Login both users
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");

            receiveLineWithTimeout(inUser1, "JOINED expected");

            // Send private message
            outUser1.println(Utils.objectToMessage(new PmReq("user2", "Hello user2")));
            outUser1.flush();

            // Verify sender receives PM_RESP
            String resp1 = receiveLineWithTimeout(inUser1, "PM_RESP expected");
            PmResp pmResp = Utils.messageToObject(resp1);
            assertEquals("OK", pmResp.status());

            // Verify receiver gets PM notification
            String resp2 = receiveLineWithTimeout(inUser2, "PM expected");
            Pm pm = Utils.messageToObject(resp2);
            assertEquals("user1", pm.username());
            assertEquals("Hello user2", pm.message());
        }

        @Test
        @Tag("RQ-U201")
        void when_users_exchange_multiple_messages() throws JsonProcessingException {
            // Setup and login
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");
            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");
            receiveLineWithTimeout(inUser1, "JOINED expected");

            // User1 sends to user2
            outUser1.println(Utils.objectToMessage(new PmReq("user2", "Message 1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "PM_RESP expected");
            String pm1 = receiveLineWithTimeout(inUser2, "PM expected");
            Pm pmObj1 = Utils.messageToObject(pm1);
            assertEquals("Message 1", pmObj1.message());

            // User2 replies to user1
            outUser2.println(Utils.objectToMessage(new PmReq("user1", "Message 2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "PM_RESP expected");
            String pm2 = receiveLineWithTimeout(inUser1, "PM expected");
            Pm pmObj2 = Utils.messageToObject(pm2);
            assertEquals("Message 2", pmObj2.message());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Private_message_fails {

        @Test
        @Tag("RQ-U201")
        void when_receiver_does_not_exist() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            outUser1.println(Utils.objectToMessage(new PmReq("nonexistent", "Hello")));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "PM_RESP expected");
            PmResp pmResp = Utils.messageToObject(response);
            assertEquals("ERROR", pmResp.status());
            assertEquals(6001, pmResp.code());
        }

        @Test
        @Tag("RQ-U201")
        void when_message_body_is_empty() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");
            receiveLineWithTimeout(inUser1, "JOINED expected");

            outUser1.println(Utils.objectToMessage(new PmReq("user2", "")));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "PM_RESP expected");
            PmResp pmResp = Utils.messageToObject(response);
            assertEquals("ERROR", pmResp.status());
            assertEquals(6002, pmResp.code());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class A_JOINED_message_is_received_by_other_clients {

        @Test
        @Tag("RQ-U212")
        void when_a_user_connects() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            // Connect user1
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            // Connect user2
            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");

            //JOINED is received by user1 when user2 connects
            /* This test is expected to fail with the given NodeJS server because the JOINED is not implemented.
             * Make sure the test works when implementing your own server in Java
             */
            String res = receiveLineWithTimeout(inUser1, "JOINED expected");
            Joined joined = Utils.messageToObject(res);

            assertEquals(new Joined("user2"), joined);
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class A_BROADCAST_is_received_by_all_other_users {

        @Test
        @Tag("RQ-U101")
        void when_a_user_sends_one() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            // Connect user1
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            // Connect user2
            outUser2.println(Utils.objectToMessage(new Logon("user2")));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "OK expected");
            /* This test is expected to fail with the given NodeJS server because the JOINED is not implemented.
             * Make sure the test works when implementing your own server in Java
             */
            receiveLineWithTimeout(inUser1, "JOINED expected");

            //send BROADCAST from user 1
            outUser1.println(Utils.objectToMessage(new BroadcastReq("messagefromuser1")));

            outUser1.flush();
            String fromUser1 = receiveLineWithTimeout(inUser1, "BROADCAST_RESP expected");
            BroadcastResp broadcastResp1 = Utils.messageToObject(fromUser1);

            assertEquals("OK", broadcastResp1.status());

            String fromUser2 = receiveLineWithTimeout(inUser2, "BROADCAST expected");
            Broadcast broadcast2 = Utils.messageToObject(fromUser2);

            assertEquals(new Broadcast("user1", "messagefromuser1"), broadcast2);

            //send BROADCAST from user 2
            outUser2.println(Utils.objectToMessage(new BroadcastReq("messagefromuser2")));
            outUser2.flush();
            fromUser2 = receiveLineWithTimeout(inUser2, "BROADCAST_RESP expected");
            BroadcastResp broadcastResp2 = Utils.messageToObject(fromUser2);
            assertEquals("OK", broadcastResp2.status());

            fromUser1 = receiveLineWithTimeout(inUser1, "BROADCAST expected");
            Broadcast broadcast1 = Utils.messageToObject(fromUser1);

            assertEquals(new Broadcast("user2", "messagefromuser2"), broadcast1);
        }

    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class An_ERROR_message_is_received {

        @Test
        @Tag("RQ-U100")
        void when_trying_to_login_with_an_already_logged_in_username() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");
            receiveLineWithTimeout(inUser2, "Initial message expected");

            // Connect user 1
            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            // Connect using same username
            outUser2.println(Utils.objectToMessage(new Logon("user1")));
            outUser2.flush();
            String resUser2 = receiveLineWithTimeout(inUser2, "LOGON_RESP expected");
            LogonResp logonResp = Utils.messageToObject(resUser2);
            assertEquals(new LogonResp("ERROR", 5000), logonResp);
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Game_initiation_succeeds {

        @Test
        @Tag("RQ-U202")
        void when_challenging_connected_user() throws JsonProcessingException {
            loginTwoUsers();

            // User1 challenges user2
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();

            // Verify initiator receives OK
            String resp1 = receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            InitGameResp initResp = Utils.messageToObject(resp1);
            assertEquals("OK", initResp.status());

            // Verify challenged user receives invitation
            String resp2 = receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            GameReq gameReq = Utils.messageToObject(resp2);
            assertEquals("user1", gameReq.username());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Game_initiation_fails {

        @Test
        @Tag("RQ-U204")
        void when_receiver_does_not_exist() throws JsonProcessingException {
            receiveLineWithTimeout(inUser1, "Initial message expected");

            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            outUser1.println(Utils.objectToMessage(new InitGameReq("nonexistent")));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            InitGameResp initResp = Utils.messageToObject(response);
            assertEquals("ERROR", initResp.status());
            assertEquals(6001, initResp.code());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Game_invitation_response_succeeds {

        @Test
        @Tag("RQ-U202")
        void when_accepting_game_invitation() throws JsonProcessingException {
            loginTwoUsers();

            // User1 challenges user2
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");

            // User2 accepts
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();

            // Verify responder receives OK
            String resp1 = receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            GameInvResp invResp1 = Utils.messageToObject(resp1);
            assertEquals("OK", invResp1.status());

            // Verify initiator receives acceptance notification
            String resp2 = receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            GameInvResp invResp2 = Utils.messageToObject(resp2);
            assertEquals("user2", invResp2.username());
            assertTrue(invResp2.accept());

            // Both receive GAME_START
            String start1 = receiveLineWithTimeout(inUser1, "GAME_START expected");
            String start2 = receiveLineWithTimeout(inUser2, "GAME_START expected");
            GameStart gs1 = Utils.messageToObject(start1);
            GameStart gs2 = Utils.messageToObject(start2);
            assertEquals(1, gs1.round());
            assertEquals(1, gs2.round());
            assertNotNull(gs1.scores());
            assertNotNull(gs2.scores());
        }

        @Test
        @Tag("RQ-U201")
        void when_rejecting_game_invitation() throws JsonProcessingException {
            loginTwoUsers();

            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");

            // User2 rejects
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", false)));
            outUser2.flush();

            String resp1 = receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            GameInvResp invResp1 = Utils.messageToObject(resp1);
            assertEquals("OK", invResp1.status());

            String resp2 = receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            GameInvResp invResp2 = Utils.messageToObject(resp2);
            assertEquals("user2", invResp2.username());
            assertFalse(invResp2.accept());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Game_round_results {

        @Test
        @Tag("RQ-U208")
        void when_both_players_choose_same_side_resulting_in_draw() throws JsonProcessingException {
            loginTwoUsers();

            // Start game
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_START expected");
            receiveLineWithTimeout(inUser2, "GAME_START expected");

            // Both choose heads (0)
            outUser1.println(Utils.objectToMessage(new GameChoiceReq(0)));
            outUser1.flush();
            String choiceResp1 = receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");
            GameChoiceResp gcr1 = Utils.messageToObject(choiceResp1);
            assertEquals("OK", gcr1.status());

            outUser2.println(Utils.objectToMessage(new GameChoiceReq(0)));
            outUser2.flush();
            String choiceResp2 = receiveLineWithTimeout(inUser2, "GAME_CHOICE_RESP expected");
            GameChoiceResp gcr2 = Utils.messageToObject(choiceResp2);
            assertEquals("OK", gcr2.status());

            // Both receive round result
            String result1 = receiveLineWithTimeout(inUser1, "GAME_ROUND_RESULT expected");
            String result2 = receiveLineWithTimeout(inUser2, "GAME_ROUND_RESULT expected");
            GameRoundResult grr1 = Utils.messageToObject(result1);
            GameRoundResult grr2 = Utils.messageToObject(result2);

            // Verify draw
            assertEquals(0, grr1.outcome());
            assertEquals(0, grr2.outcome());
            assertNull(grr1.roundWinner());
            assertNull(grr2.roundWinner());
        }

        @Test
        @Tag("RQ-U208")
        void when_players_choose_different_sides_and_winner_determined_by_coin() throws JsonProcessingException {
            loginTwoUsers();

            // Start game
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_START expected");
            receiveLineWithTimeout(inUser2, "GAME_START expected");

            // User1 chooses heads (0), user2 chooses tails (1)
            outUser1.println(Utils.objectToMessage(new GameChoiceReq(0)));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");

            outUser2.println(Utils.objectToMessage(new GameChoiceReq(1)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_CHOICE_RESP expected");

            // Receive round results
            String result1 = receiveLineWithTimeout(inUser1, "GAME_ROUND_RESULT expected");
            String result2 = receiveLineWithTimeout(inUser2, "GAME_ROUND_RESULT expected");
            GameRoundResult grr1 = Utils.messageToObject(result1);
            GameRoundResult grr2 = Utils.messageToObject(result2);

            // Verify winner matches coin result
            int coinResult = grr1.coinResult();
            String expectedWinner = (coinResult == 0) ? "user1" : "user2";
            assertEquals(expectedWinner, grr1.roundWinner());
            assertEquals(expectedWinner, grr2.roundWinner());

            // Verify outcomes are opposite
            assertTrue((grr1.outcome() == 1 && grr2.outcome() == 2) ||
                    (grr1.outcome() == 2 && grr2.outcome() == 1));
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Game_choice_submission_fails {

        @ParameterizedTest
        @ValueSource(ints = {-1, 2, 3, 999})
        @Tag("RQ-U203")
        void when_invalid_choice_is_submitted(int invalidChoice) throws JsonProcessingException {
            loginTwoUsers();

            // Start game
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_START expected");
            receiveLineWithTimeout(inUser2, "GAME_START expected");

            // Submit invalid choice
            outUser1.println(Utils.objectToMessage(new GameChoiceReq(invalidChoice)));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");
            GameChoiceResp gcr = Utils.messageToObject(response);
            assertEquals("ERROR", gcr.status());
            assertEquals(9002, gcr.code());
        }

        @Test
        @Tag("RQ-U203")
        void when_submitting_choice_twice_in_same_round() throws JsonProcessingException {
            loginTwoUsers();

            // Start game
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_START expected");
            receiveLineWithTimeout(inUser2, "GAME_START expected");

            // Submit first choice
            outUser1.println(Utils.objectToMessage(new GameChoiceReq(0)));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");

            // Try to submit again
            outUser1.println(Utils.objectToMessage(new GameChoiceReq(1)));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");
            GameChoiceResp gcr = Utils.messageToObject(response);
            assertEquals("ERROR", gcr.status());
            assertEquals(9003, gcr.code());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class Complete_game_flow {

        @Test
        @Tag("RQ-U206")
        void when_playing_game_to_completion() throws JsonProcessingException {
            loginTwoUsers();

            // Start game
            outUser1.println(Utils.objectToMessage(new InitGameReq("user2")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_GAME_RESP expected");
            receiveLineWithTimeout(inUser2, "GAME_REQ expected");
            outUser2.println(Utils.objectToMessage(new GameInvReq("user1", true)));
            outUser2.flush();
            receiveLineWithTimeout(inUser2, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_INV_RESP expected");
            receiveLineWithTimeout(inUser1, "GAME_START expected");
            receiveLineWithTimeout(inUser2, "GAME_START expected");

            // Play rounds until someone wins (max 5 rounds to win 3)
            boolean gameEnded = false;
            int maxRounds = 10; // Safety limit

            for (int i = 0; i < maxRounds && !gameEnded; i++) {
                // User1 chooses heads, user2 chooses tails
                outUser1.println(Utils.objectToMessage(new GameChoiceReq(0)));
                outUser1.flush();
                receiveLineWithTimeout(inUser1, "GAME_CHOICE_RESP expected");

                outUser2.println(Utils.objectToMessage(new GameChoiceReq(1)));
                outUser2.flush();
                receiveLineWithTimeout(inUser2, "GAME_CHOICE_RESP expected");

                // Receive round results
                String result1 = receiveLineWithTimeout(inUser1, "GAME_ROUND_RESULT or GAME_END expected");
                String result2 = receiveLineWithTimeout(inUser2, "GAME_ROUND_RESULT or GAME_END expected");

                // Check if game ended
                if (result1.startsWith("GAME_END")) {
                    gameEnded = true;
                    GameEnd ge1 = Utils.messageToObject(result1);
                    GameEnd ge2 = Utils.messageToObject(result2);

                    // Verify game end
                    assertNotNull(ge1.winner());
                    assertNotNull(ge1.loser());
                    assertEquals(ge1.winner(), ge2.winner());
                    assertEquals(ge1.loser(), ge2.loser());
                    assertTrue((ge1.result() == 1 && ge2.result() == 2) ||
                            (ge1.result() == 2 && ge2.result() == 1));

                    // Verify final scores
                    int winnerScore = ge1.finalScores().get(ge1.winner());
                    assertEquals(3, winnerScore);
                } else if (result1.startsWith("GAME_ROUND_RESULT")) {
                    GameRoundResult grr1 = Utils.messageToObject(result1);
                    if (grr1.scores().values().stream().anyMatch(s -> s >= 3)) {
                        // Game is over, expect GAME_END
                        String endMsg1 = receiveLineWithTimeout(inUser1, "GAME_END expected");
                        String endMsg2 = receiveLineWithTimeout(inUser2, "GAME_END expected");

                        GameEnd ge1 = Utils.messageToObject(endMsg1);
                        GameEnd ge2 = Utils.messageToObject(endMsg2);

                        // Verify game end
                        assertNotNull(ge1.winner());
                        assertNotNull(ge1.loser());
                        assertEquals(ge1.winner(), ge2.winner());
                        assertEquals(ge1.loser(), ge2.loser());
                        assertTrue((ge1.result() == 1 && ge2.result() == 2) ||
                                (ge1.result() == 2 && ge2.result() == 1));

                        // Verify final scores
                        int winnerScore = ge1.finalScores().get(ge1.winner());
                        assertEquals(3, winnerScore);

                        gameEnded = true;
                    }
                }
            }

            assertTrue(gameEnded, "Game should have ended within max rounds");
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class File_transfer_initiation_succeeds {

        @Test
        @Tag("RQ-U300")
        void when_initiating_transfer_to_connected_user() throws Exception {
            loginTwoUsers();

            // Create test file
            File testFile = createTestFile("test.txt", "Hello World");
            String checksum = calculateSHA256(testFile);
            long size = testFile.length();

            // Initiate transfer
            outUser1.println(Utils.objectToMessage(new InitFileReq("user2", "test.txt", size, checksum)));
            outUser1.flush();

            // Verify sender receives port
            String resp1 = receiveLineWithTimeout(inUser1, "INIT_FILE_RESP expected");
            InitFileResp initResp = Utils.messageToObject(resp1);
            assertEquals("OK", initResp.status());
            assertNotNull(initResp.port());

            // Verify receiver gets notification
            String resp2 = receiveLineWithTimeout(inUser2, "FILE_REQ expected");
            FileReq fileReq = Utils.messageToObject(resp2);
            assertEquals("user1", fileReq.sender());
            assertEquals("test.txt", fileReq.filename());
            assertEquals(size, fileReq.size());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class File_transfer_initiation_fails {

        @Test
        @Tag("RQ-U300")
        void when_receiver_does_not_exist() throws Exception {
            receiveLineWithTimeout(inUser1, "Initial message expected");

            outUser1.println(Utils.objectToMessage(new Logon("user1")));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "OK expected");

            File testFile = createTestFile("test.txt", "Hello");
            String checksum = calculateSHA256(testFile);

            outUser1.println(Utils.objectToMessage(new InitFileReq("nonexistent", "test.txt", testFile.length(), checksum)));
            outUser1.flush();

            String response = receiveLineWithTimeout(inUser1, "INIT_FILE_RESP expected");
            InitFileResp initResp = Utils.messageToObject(response);
            assertEquals("ERROR", initResp.status());
            assertEquals(6001, initResp.code());
        }
    }

    @Nested
    @IndicativeSentencesGeneration(separator = " -> ", generator = DisplayNameGenerator.ReplaceUnderscores.class)
    class File_transfer_acceptance_succeeds {

        @Test
        @Tag("RQ-U301")
        void when_accepting_file_transfer() throws Exception {
            loginTwoUsers();

            File testFile = createTestFile("test.txt", "Content");
            String checksum = calculateSHA256(testFile);

            // Initiate transfer
            outUser1.println(Utils.objectToMessage(new InitFileReq("user2", "test.txt", testFile.length(), checksum)));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_FILE_RESP expected");
            receiveLineWithTimeout(inUser2, "FILE_REQ expected");

            // Accept transfer
            outUser2.println(Utils.objectToMessage(new FileInvReq("user1", true)));
            outUser2.flush();

            String response = receiveLineWithTimeout(inUser2, "FILE_INV_RESP expected");
            FileInvResp invResp = Utils.messageToObject(response);
            assertEquals("OK", invResp.status());
            assertNotNull(invResp.port());
        }

        @Test
        @Tag("RQ-U301")
        void when_rejecting_file_transfer() throws Exception {
            loginTwoUsers();

            File testFile = createTestFile("test.txt", "Content");
            String checksum = calculateSHA256(testFile);

            outUser1.println(Utils.objectToMessage(new InitFileReq("user2", "test.txt", testFile.length(), checksum)));
            outUser1.flush();
            receiveLineWithTimeout(inUser1, "INIT_FILE_RESP expected");
            receiveLineWithTimeout(inUser2, "FILE_REQ expected");

            // Reject transfer
            outUser2.println(Utils.objectToMessage(new FileInvReq("user1", false)));
            outUser2.flush();

            String response = receiveLineWithTimeout(inUser2, "FILE_INV_RESP expected");
            FileInvResp invResp = Utils.messageToObject(response);
            assertEquals("OK", invResp.status());
        }
    }

    private void loginTwoUsers() throws JsonProcessingException {
        receiveLineWithTimeout(inUser1, "Initial message expected");
        receiveLineWithTimeout(inUser2, "Initial message expected");

        outUser1.println(Utils.objectToMessage(new Logon("user1")));
        outUser1.flush();
        receiveLineWithTimeout(inUser1, "OK expected");

        outUser2.println(Utils.objectToMessage(new Logon("user2")));
        outUser2.flush();
        receiveLineWithTimeout(inUser2, "OK expected");

        receiveLineWithTimeout(inUser1, "JOINED expected");
    }

    private File createTestFile(String filename, String content) throws IOException {
        File file = new File("/tmp/" + filename);
        Files.writeString(file.toPath(), content);
        return file;
    }

    private String calculateSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] hashBytes = digest.digest(fileBytes);
        return bytesToHex(hashBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String receiveLineWithTimeout(BufferedReader reader, String message) {
        return assertTimeoutPreemptively(ofMillis(pingTimeMsDeltaAllowed), reader::readLine, message);
    }

}