package com.collab.platform.service;

import com.collab.platform.dto.Dtos.FileRes;
import com.collab.platform.entity.*;
import com.collab.platform.exception.ApiException;
import com.collab.platform.repository.FileRepository;
import com.collab.platform.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {
    private final FileRepository files;
    private final TaskRepository tasks;
    private final AccessService access;
    private final ActivityService activity;
    private final Path root;

    public FileService(FileRepository files, TaskRepository tasks, AccessService access,
                       ActivityService activity, @Value("${app.upload-dir}") String dir) throws IOException {
        this.files = files;
        this.tasks = tasks;
        this.access = access;
        this.activity = activity;
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Transactional(readOnly = true)
    public List<FileRes> list(Long projectId, User user) {
        access.member(projectId, user.getId());
        return files.findByProjectIdOrderByUploadedAtDesc(projectId).stream().map(this::toRes).toList();
    }

    @Transactional
    public FileRes upload(Long projectId, Long taskId, User user, MultipartFile file) throws IOException {
        access.member(projectId, user.getId());
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a file to upload");
        Project project = access.project(projectId);

        Task task = null;
        if (taskId != null) {
            task = tasks.findById(taskId)
                    .filter(t -> t.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That task is not in this project"));
        }

        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        if (original.contains("..")) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file name");
        String stored = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Files.copy(file.getInputStream(), root.resolve(stored), StandardCopyOption.REPLACE_EXISTING);

        FileAttachment f = new FileAttachment();
        f.setProject(project);
        f.setTask(task);
        f.setUploadedBy(user);
        f.setFileName(original);
        f.setStoredName(stored);
        f.setContentType(file.getContentType());
        f.setSizeBytes(file.getSize());
        files.save(f);
        activity.log(project, user, "uploaded " + original);
        return toRes(f);
    }

    @Transactional(readOnly = true)
    public FileAttachment getForDownload(Long id, User user) {
        FileAttachment f = files.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
        access.member(f.getProject().getId(), user.getId());
        return f;
    }

    public Resource load(FileAttachment f) {
        try {
            Path path = root.resolve(f.getStoredName()).normalize();
            Resource r = new UrlResource(path.toUri());
            if (!path.startsWith(root) || !r.exists())
                throw new ApiException(HttpStatus.NOT_FOUND, "The file is missing from storage");
            return r;
        } catch (MalformedURLException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The file is missing from storage");
        }
    }

    @Transactional
    public void delete(Long id, User user) throws IOException {
        FileAttachment f = files.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
        ProjectMember m = access.member(f.getProject().getId(), user.getId());
        if (!access.isManager(m) && !f.getUploadedBy().getId().equals(user.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only managers or the uploader can delete this file");
        activity.log(f.getProject(), user, "deleted file " + f.getFileName());
        files.delete(f);
        Files.deleteIfExists(root.resolve(f.getStoredName()).normalize());
    }

    private FileRes toRes(FileAttachment f) {
        Task t = f.getTask();
        return new FileRes(f.getId(), f.getFileName(), f.getSizeBytes(), f.getUploadedBy().getName(),
                t == null ? null : t.getId(), t == null ? null : t.getTitle(), f.getUploadedAt());
    }
}
