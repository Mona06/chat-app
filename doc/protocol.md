# Protocol description

This client-server protocol describes the following scenarios:
- Setting up a connection between client and server.
- Broadcasting a message to all connected clients.
- Periodically sending heartbeat to connected clients.
- Disconnection from the server.
- Handling invalid messages.

In the description below, `C -> S` represents a message from the client `C` is sent to server `S`. When applicable, `C` is extended with a number to indicate a specific client, e.g., `C1`, `C2`, etc. The keyword `others` is used to indicate all other clients except for the client who made the request. Messages can contain a JSON body. Text shown between `<` and `>` are placeholders.

The protocol follows the formal JSON specification, RFC 8259, available on https://www.rfc-editor.org/rfc/rfc8259.html

All messages may end using Linux line endings (\n) or windows line endings (\r\n) and client and server should interpret both cases as valid messages.

# 1. Establishing a connection

The client first sets up a socket connection to which the server responds with a welcome message. The client supplies a username on which the server responds with an OK if the username is accepted or an ERROR with a number in case of an error.
_Note:_ A username may only consist of characters, numbers, and underscores ('_') and has a length between 3 and 14 characters.

## 1.1 Happy flow

Client sets up the connection with server.
```
S -> C: HI {"version": "<server version number>"}
```
- `<server version number>`: the semantic version number of the server.

After a while when the client logs the user in:
```
C -> S: LOGON {"username":"<username>"}
S -> C: LOGON_RESP {"status":"OK"}
```

- `<username>`: the username of the user that needs to be logged in.

To other clients (Only applicable when working on Level 2):
```
S -> others: JOINED {"username":"<username>"}
```

## 1.2 Unhappy flow
```
S -> C: LOGON_RESP {"status":"ERROR", "code":<error code>}
```      
Possible `<error code>`:

| Error code | Description                              |
|------------|------------------------------------------|
| 5000       | User with this name already exists       |
| 5001       | Username has an invalid format or length |      
| 5002       | Already logged in                        |

# 2. Broadcast message

Sends a message from a client to all other clients. The sending client does not receive the message itself but gets a confirmation that the message has been sent.

## 2.1 Happy flow

```
C -> S: BROADCAST_REQ {"message":"<message>"}
S -> C: BROADCAST_RESP {"status":"OK"}
```
- `<message>`: the message that must be sent.

Other clients receive the message as follows:
```
S -> others: BROADCAST {"username":"<username>","message":"<message>"}   
```   
- `<username>`: the username of the user that is sending the message.

## 2.2 Unhappy flow

```
S -> C: BROADCAST_RESP {"status": "ERROR", "code": <error code>}
```
Possible `<error code>`:

| Error code | Description            |
|------------|------------------------|
| 6000       | User is not logged in  |

# 3. Heartbeat message

Sends a ping message to the client to check whether the client is still active. The receiving client should respond with a pong message to confirm it is still active. If after 3 seconds no pong message has been received by the server, the connection to the client is closed. Before closing, the client is notified with a HANGUP message, with reason code 7000.

The server sends a ping message to a client every 10 seconds. The first ping message is send to the client 10 seconds after the client is logged in.

When the server receives a PONG message while it is not expecting one, a PONG_ERROR message will be returned.

## 3.1 Happy flow

```
S -> C: PING
C -> S: PONG
```     

## 3.2 Unhappy flow

```
S -> C: HANGUP {"reason": <reason code>}
[Server disconnects the client]
```      
Possible `<reason code>`:

| Reason code | Description      |
|-------------|------------------|
| 7000        | No pong received |    

```
S -> C: PONG_ERROR {"code": <error code>}
```
Possible `<error code>`:

| Error code | Description         |
|------------|---------------------|
| 8000       | Pong without ping   |    

# 4. Termination of the connection

When the connection needs to be terminated, the client sends a bye message. This will be answered (with a BYE_RESP message) after which the server will close the socket connection.

## 4.1 Happy flow
```
C -> S: BYE
S -> C: BYE_RESP {"status":"OK"}
[Server closes the socket connection]
```

Other, still connected clients, clients receive:
```
S -> others: LEFT {"username":"<username>"}
```

## 4.2 Unhappy flow

- None

# 5. Invalid message header

If the client sends an invalid message header (not defined above), the server replies with an unknown command message. The client remains connected.

Example:
```
C -> S: MSG This is an invalid message
S -> C: UNKNOWN_COMMAND
```

# 6. Invalid message body

If the client sends a valid message, but the body is not valid JSON, the server replies with a pars error message. The client remains connected.

Example:
```
C -> S: BROADCAST_REQ {"aaaa}
S -> C: PARSE_ERROR
```

# 7. List connected users

## 7.1 Happy flow

```
C -> S: CL_REQ
S -> C: CL_RESP {“status”: “OK”, “users”: ["<username1>", "<username2>",...]}
```

- `<usernameX>`: represents usernames of connected users.

## 7.2 Unhappy flow

```
S -> C: CL_RESP {"status":"ERROR", "code":<error code>}
```      

Possible `<error code>`:

| Error code | Description           | 
|------------|-----------------------|
| 6000       | User is not logged in |

# 8. Send private message

Sends a private message to a client. The sending client does not receive the message himself but gets a confirmation
that the message has been sent.

## 8.1 Happy flow

```
C -> S: PM_REQ {“receiver”: "<username>", “message”: “<message>”}
S -> C: PM_RESP {"status": “OK”}
```

- `<receiver>`: the username of the user who is to receive the private message.

Other clients receive the message as follows:

```
S -> receiver: PM {"username":"<username>","message":"<message>"}   
```   

- `<username>`: the username of the user that is sending the message.

## 8.2 Unhappy flow

```
S -> C: PM_RESP {"status": "ERROR", "code": <error code>}
```

Possible `<error code>`:

| Error code | Description                  |
|------------|------------------------------|
| 6000       | User is not logged in        |
| 6001       | Receiver is not connected    |
| 6002       | Message body cannot be empty |

# 9. Heads and tails game

# 9.1 Initiate game

## 9.1.1 Happy flow

Client A initiates the game

```
C -> S: INIT_GAME_REQ {"receiver": "<username>"}
S -> C: INIT_GAME_RESP {"status": "OK"}
```

Client B receives the message as follows:

 ```
 S -> receiver: GAME_REQ {"username": "<usernameA>"}
 ```

- `<usernameA>`: the username of the challenger client A.

## 9.1.2 Unhappy flow

```
S -> C: INIT_GAME_RESP {"status":"ERROR", "code": <error code>}
```

When the server response returns error code 9000, the players are included in the json object

```
S -> C: INIT_GAME_RESP {"status":"ERROR", "code": <error code>, "player1": "<username1>", "player2": "<username2>"}
```

Possible `<error code>`:

| Error code | Description                                 |
|------------|---------------------------------------------|
| 6000       | User is not logged in                       |
| 6001       | Receiver is not connected                   |
| 9000       | Another game is running between other users |

# 9.2 Respond to game invite

## 9.2.1 Happy flow

```
C -> S: GAME_INV_REQ {"username": "<username>", "accept": <accepted>}
S -> C: GAME_INV_RESP {"status": "OK"}
```

- `<accepted>`: boolean value (true or false) indicating whether the game invite has been accepted or rejected

Client A receives the message as follows:

```
 S -> C: GAME_INV_RESP {"username": "<username>", "accept": <accepted>}
```

If accepted, both players receive the game start notification:
```
S -> both players: GAME_START {"round": 1, "scores": {"<player1>": 0, "<player2>": 0}}
```

## 9.2.2 Unhappy flow

```
S -> C: GAME_INV_RESP {"status": "ERROR", "code": <error code>}
```

| Error code | Description         |
|------------|---------------------|
| 9001       | No game in progress |

# 9.3 Make a choice (Heads or tails)

Choice codes:
- 0: heads
- 1: tails

## 9.3.1 Happy flow

Player submits their choice:

```
C -> S: GAME_CHOICE_REQ {"choice": <choice>}
S -> C: GAME_CHOICE_RESP {"status": "OK"}
```

-`<choice>`: numeric code representing the player's choice (0 for heads, 1 for tails)

## 9.3.2 Unhappy flow

```
S -> C: GAME_CHOICE_RESP {"status": "ERROR", "code": <error code>}
```

| Error code | Description            |
|------------|------------------------|
| 9001       | No game in progress    |
| 9002       | Invalid choice (must be 0 or 1) |
| 9003       | Choice already submitted for this round  |

# 9.4 Game round update

After both players have made their choices, the server flips the coin and sends the result

Outcome codes:
- 0: draw (both players chose the same side)
- 1: win (you won the round)
- 2: loss (you lost the round)

## 9.4.1 Both players choose the same side
```
S -> both players: GAME_ROUND_RESULT {
    "round": <round_number>,
    "coinResult": <coin_result>,
    "yourChoice": <your_choice>,
    "opponentChoice": <opponent_choice>,
    "outcome": 0,
    "scores": {"<player1>": <score1>, "<player2>": <score2>}
}
```

## 9.4.2 Players choose different sides

```
S -> both clients: GAME_ROUND_RESULT {
    "round": <round_number>,
    "coinResult": <coin_result>,
    "yourChoice": <your_choice>,
    "opponentChoice": <opponent_choice>,
    "outcome": <outcome>,
    "roundWinner": "<username>",
    "scores": {"<player1>": <score1>, "<player2>": <score2>}
}
```

- `<round_number>`: current round number (starts at 1)
- `<coin_result>`: numeric code for the coin flip result (0 for heads, 1 for tails)
- `<your_choice>`: numeric code for the choice the receiving player made (0 for heads, 1 for tails)
- `<opponent_choice>`: numeric code for the choice the opponent made (0 for heads, 1 for tails)
- `<outcome>`: 1 if you won the round, 2 if you lost the round
- `<roundWinner>`: username of the player who won this round (only present when outcome is not 0)
- `<scores>`: current scores for both players


# 9.5 Game end results

Sends out the end game results to the players when a player scores 3 points

## Happy flow

Both players receive the outcome of the game

```
S -> both players: GAME_END {"result": <result>, "winner": "<winner>", "loser": "<loser>", "finalScores": {"<player1>": <score1>, "<player2>": <score2>}}
```

- `<result>`: 1 if you won, 2 if you lost (first player to reach 3 points wins)
- `<winner>`: username of the player who won
- `<loser>`: username of the player who lost
- `<finalScores>`: the final score of each player

## Unhappy flow

none

# 10 File Transfer

# 10.1 Initiate File Transfer

## 10.1.1 Happy flow

```
C -> S: INIT_FILE_REQ {
    "receiver": "<username>",
    "filename": "<filename>",
    "size": <filesize>,
    "checksum": "<sha256>"
}
S -> C: INIT_FILE_RESP {"status": "OK", "port": <port>}
```

- `<username>`: username of intended file recipient
- `<filename>`: name of file to be transferred
- `<filesize>`: size of file in bytes
- `<sha256`>: SHA-256 checksum of file contents
- `<port>`: port number for dedicated file transfer connection

Receiving client gets notified:

```
S -> receiver: FILE_REQ {
    "sender": "<username>",
    "filename": "<filename>",
    "size": <filesize>
}
```

## 10.1.2 Unhappy flow

```
S -> C: INIT_FILE_RESP {"status": "ERROR", "code": <error code>}
```

| Error code | Description               |
|------------|---------------------------|
| 6000       | User is not logged in     |
| 6001       | Receiver is not connected |

# 10.2 File Transfer Response

## 10.2.1 Happy flow

```
C -> S: FILE_INV_REQ {"username": "<username>", "accept": <accepted>}
S -> C: FILE_INV_RESP {"status": "OK", "port": <port>}
```

- `<port>`: port number for dedicated file transfer connection
- `<accepted>`: indicates whether the game invite has been accepted or rejected

# File Transfer Completion

## Happy flow

```
S -> receiver: FILE_COMPLETE {
    "sender": "<username>",
    "filename": "<filename>",
    "checksum": "<sha256>"
}
S -> sender: FILE_DELIVERED {
    "receiver": "<username>",
    "filename": "<filename>"
}
```

## Unhappy flow

none