CREATE TABLE Events (
 messageId UUID PRIMARY KEY,
 conversationId UUID NOT NULL,
 type VARCHAR NOT NULL,
 payload VARCHAR NOT NULL,
 time TIMESTAMP NOT NULL
);

CREATE TABLE Channels (
 conversationId UUID PRIMARY KEY,
 botId UUID
);