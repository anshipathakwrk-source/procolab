package com.collab.platform.controller;

import com.collab.platform.dto.Dtos.FileRes;
import com.collab.platform.entity.FileAttachment;
import com.collab.platform.entity.User;
import com.collab.platform.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {
    private final FileService service;

    @GetMapping("/projects/{pid}/files")
    public List<FileRes> list(@AuthenticationPrincipal User u, @PathVariable Long pid) { return service.list(pid, u); }

    @PostMapping(value = "/projects/{pid}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileRes upload(@AuthenticationPrincipal User u, @PathVariable Long pid,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(value = "taskId", required = false) Long taskId) throws IOException {
        return service.upload(pid, taskId, u, file);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal User u, @PathVariable Long id) {
        FileAttachment f = service.getForDownload(id, u);
        Resource body = service.load(f);
        MediaType type = f.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(f.getContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @DeleteMapping("/files/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User u, @PathVariable Long id) throws IOException { service.delete(id, u); }
}
