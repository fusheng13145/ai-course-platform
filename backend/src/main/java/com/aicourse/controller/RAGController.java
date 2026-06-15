package com.aicourse.controller;

import com.aicourse.service.RAGService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * RAG知识库控制器
 * 提供基于知识库的智能问答接口
 */
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    private final RAGService ragService;

    public RAGController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public String ask(
            @RequestParam String question,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "3") int topK) {
        return ragService.ask(question, courseId, topK);
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String addDocument(
            @RequestParam Long courseId,
            @RequestParam String documentName,
            @RequestBody String content) {
        return ragService.addDocument(courseId, documentName, content);
    }

    @GetMapping("/documents")
    public String listDocuments(@RequestParam(required = false) Long courseId) {
        return ragService.listDocuments(courseId);
    }

    @PutMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String updateDocument(
            @PathVariable Long documentId,
            @RequestBody String content) {
        return ragService.updateDocument(documentId, content);
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String deleteDocument(@PathVariable Long documentId) {
        return ragService.deleteDocument(documentId);
    }

    @PostMapping("/index/build/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String buildIndex(@PathVariable Long courseId) {
        return ragService.buildIndex(courseId);
    }

    @DeleteMapping("/index/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String clearIndex(@PathVariable Long courseId) {
        return ragService.clearIndex(courseId);
    }

    @PostMapping("/retrieve")
    public String retrieve(
            @RequestParam String query,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "5") int topK) {
        return ragService.retrieve(query, courseId, topK);
    }
}