package com.coderushoj.worker.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class OutputComparator {
    public boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    String normalize(String value) {
        if (value == null) {
            return "";
        }
        List<String> lines = new ArrayList<>(Arrays.asList(value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, lines.get(i).stripTrailing());
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return String.join("\n", lines);
    }
}
