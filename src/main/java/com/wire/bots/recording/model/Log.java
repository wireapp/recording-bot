package com.wire.bots.recording.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class Log {
    public Kibana securehold;

    public static class Kibana {
        public UUID id;
        public String type;
        public UUID conversationID;
        public String conversationName;
        public List<String> participants = new ArrayList<>();
        public Long sent;
        public String sender;
        public UUID messageID;
        public String text;
        public UUID from;
    }
}