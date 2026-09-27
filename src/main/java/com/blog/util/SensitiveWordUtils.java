package com.blog.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简易敏感词过滤（DFA 会引入额外维护成本，这里用包含匹配，词表规模小、性能足够）。
 */
@Slf4j
public final class SensitiveWordUtils {

    private static volatile List<String> WORDS = Collections.emptyList();

    private SensitiveWordUtils() {
    }

    public static void init(List<String> words) {
        List<String> list = new ArrayList<>();
        if (words != null) {
            for (String w : words) {
                if (w != null && !w.isBlank()) {
                    list.add(w.trim().toLowerCase());
                }
            }
        }
        WORDS = list;
    }

    public static boolean contains(String text) {
        return !find(text).isEmpty();
    }

    public static List<String> find(String text) {
        if (text == null || text.isBlank() || WORDS.isEmpty()) {
            return Collections.emptyList();
        }
        String lower = text.toLowerCase();
        List<String> hits = new ArrayList<>();
        for (String word : WORDS) {
            if (lower.contains(word)) {
                hits.add(word);
            }
        }
        return hits;
    }

    /**
     * 将命中的敏感词替换为 ***。
     */
    public static String filter(String text) {
        if (text == null || text.isBlank() || WORDS.isEmpty()) {
            return text;
        }
        String result = text;
        for (String word : WORDS) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
        }
        return result;
    }
}
