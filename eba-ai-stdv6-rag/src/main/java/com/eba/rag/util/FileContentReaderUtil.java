package com.eba.rag.util;

import java.io.File;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-29 11:25:12
 * @Desc:
 */
public class FileContentReaderUtil {

    /**
     * 读取文件内容为字符串（UTF-8）
     */
    public static String readFileContent(File file) {
        try {
            return java.nio.file.Files.readString(file.toPath());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
