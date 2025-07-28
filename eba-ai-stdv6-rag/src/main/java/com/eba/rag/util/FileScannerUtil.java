package com.eba.rag.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-16 15:19:38
 * @Desc:
 */
public class FileScannerUtil {
    /**
     * 扫描指定目录下的所有源码文件（递归）
     * @param rootPath 根路径
     * @param extensions 文件扩展名列表（如 .java, .vue, .js）
     * @return 所有匹配的文件列表
     */
    public static List<File> scanFiles(String rootPath, List<String> extensions) {
        List<File> result = new ArrayList<>();
        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            System.err.println("路径不存在或不是目录：" + rootPath);
            return result;
        }
        recursiveScan(root, extensions, result);
        return result;
    }

    private static void recursiveScan(File dir, List<String> extensions, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                recursiveScan(file, extensions, result);
            } else {
                for (String ext : extensions) {
                    if (file.getName().toLowerCase().endsWith(ext.toLowerCase())) {
                        result.add(file);
                        break;
                    }
                }
            }
        }
    }
}
