package com.eba.rag;

import com.eba.AiStdv6RagApplication;
import com.eba.rag.util.FileScannerUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

@SpringBootTest(classes = AiStdv6RagApplication.class)
class AiStdv6RagApplicationTests {

    @Test
    void contextLoads() {

        String rootPath = "D:\\Code\\Java\\zxdk";
        List<String> extensions = List.of(".java", ".xml", ".yml", ".properties");
        List<File> files = FileScannerUtil.scanFiles(rootPath, extensions);
    }



}
