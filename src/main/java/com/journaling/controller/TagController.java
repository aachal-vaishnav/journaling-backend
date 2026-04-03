package com.journaling.controller;

import com.journaling.dto.TagRequest;
import com.journaling.dto.TagResponse;
import com.journaling.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tagService.listTags(userId(userDetails)));
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TagRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.createTag(userId(userDetails), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        tagService.deleteTag(userId(userDetails), id);
        return ResponseEntity.noContent().build();
    }

    private Long userId(UserDetails userDetails) {
        return Long.parseLong(userDetails.getUsername());
    }
}
