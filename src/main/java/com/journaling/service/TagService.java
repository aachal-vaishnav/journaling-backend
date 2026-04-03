package com.journaling.service;

import com.journaling.dto.TagRequest;
import com.journaling.dto.TagResponse;
import com.journaling.entity.Tag;
import com.journaling.entity.User;
import com.journaling.exception.DuplicateResourceException;
import com.journaling.exception.ResourceNotFoundException;
import com.journaling.repository.TagRepository;
import com.journaling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Cacheable(value = "tags", key = "#userId")
    @Transactional(readOnly = true)
    public List<TagResponse> listTags(Long userId) {
        return tagRepository.findByUserIdOrderByName(userId)
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    @CacheEvict(value = "tags", key = "#userId")
    @Transactional
    public TagResponse createTag(Long userId, TagRequest request) {
        if (tagRepository.existsByUserIdAndName(userId, request.name())) {
            throw new DuplicateResourceException("Tag already exists: " + request.name());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        Tag tag = Tag.builder()
                .user(user)
                .name(request.name())
                .color(request.color() != null ? request.color() : "#6366f1")
                .build();

        tagRepository.save(tag);
        return TagResponse.from(tag);
    }

    @CacheEvict(value = "tags", key = "#userId")
    @Transactional
    public void deleteTag(Long userId, Long tagId) {
        Tag tag = tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", tagId));
        tagRepository.delete(tag);
    }
}
