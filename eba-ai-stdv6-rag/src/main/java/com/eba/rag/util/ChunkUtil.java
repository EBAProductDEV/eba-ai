package com.eba.rag.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-29 11:26:51
 * @Desc:
 */
public class ChunkUtil {

    /**
     * 将文本切分成固定大小的 chunk（可加重叠 sliding window）
     */
    public static List<String> splitToChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += (chunkSize - overlap);
        }
        return chunks;
    }
    /**
     * 把文件切分成一个java方法片段chunks（java文件）
     */
    public class JavaMethodSplitter {

        private static final Pattern METHOD_PATTERN = Pattern.compile(
                "(public|protected|private|static|\\s)+[\\w\\<\\>\\[\\]]+\\s+\\w+\\s*\\([^\\)]*\\)\\s*\\{",
                Pattern.MULTILINE);

        public static List<String> splitJavaMethods(String code) {
            List<String> chunks = new ArrayList<>();
            Matcher matcher = METHOD_PATTERN.matcher(code);
            List<Integer> startIndexes = new ArrayList<>();

            while (matcher.find()) {
                startIndexes.add(matcher.start());
            }

            for (int i = 0; i < startIndexes.size(); i++) {
                int start = startIndexes.get(i);
                int end = (i + 1 < startIndexes.size()) ? startIndexes.get(i + 1) : code.length();
                chunks.add(code.substring(start, end).trim());
            }

            return chunks;
        }
    }

    /**
     * 把文件切分成方法片段chunks(vue文件)
     */
    public class VueSplitter {

        public static Map<String, String> splitVueSections(String vueCode) {
            Map<String, String> sections = new HashMap<>();
            sections.put("template", extractSection(vueCode, "template"));
            sections.put("script", extractSection(vueCode, "script"));
            sections.put("style", extractSection(vueCode, "style"));
            return sections;
        }

        private static String extractSection(String code, String tag) {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*>([\\s\\S]*?)</" + tag + ">", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(code);
            return matcher.find() ? matcher.group(1).trim() : "";
        }

        public static List<String> splitJsFunctions(String jsCode) {
            Pattern functionPattern = Pattern.compile("(\\w+)\\s*\\([^)]*\\)\\s*\\{", Pattern.MULTILINE);
            Matcher matcher = functionPattern.matcher(jsCode);
            List<Integer> startIndexes = new ArrayList<>();
            while (matcher.find()) {
                startIndexes.add(matcher.start());
            }

            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < startIndexes.size(); i++) {
                int start = startIndexes.get(i);
                int end = (i + 1 < startIndexes.size()) ? startIndexes.get(i + 1) : jsCode.length();
                chunks.add(jsCode.substring(start, end).trim());
            }

            return chunks;
        }
    }
}
