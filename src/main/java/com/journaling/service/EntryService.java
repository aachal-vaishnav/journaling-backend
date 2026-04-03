package com.journaling.service;

import com.journaling.dto.*;
import com.journaling.entity.Entry;
import com.journaling.entity.Mood;
import com.journaling.entity.Tag;
import com.journaling.entity.User;
import com.journaling.exception.ResourceNotFoundException;
import com.journaling.metrics.JournalingMetrics;
import com.journaling.repository.EntryRepository;
import com.journaling.repository.TagRepository;
import com.journaling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final EntryRepository entryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final JournalingMetrics metrics;

    @Transactional(readOnly = true)
    public PagedResponse<EntryResponse> listEntries(
            Long userId, String search, String mood, int page, int limit
    ) {
        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        Page<Entry> result;

        if (search != null && !search.isBlank()) {
            long start = System.currentTimeMillis();
            // MySQL FULLTEXT search — avoids full table scan via inverted index
            result = entryRepository.fullTextSearch(userId, search + "*", pageable);
            metrics.incrementSearch();
            metrics.recordSearchLatency(System.currentTimeMillis() - start);
        } else if (mood != null && !mood.isBlank()) {
            Mood moodEnum = parseMood(mood);
            result = entryRepository.findByUserIdAndMood(userId, moodEnum, pageable);
        } else {
            // EntityGraph Join Fetch — eliminates N+1: tags loaded in one query
            result = entryRepository.findByUserId(userId, pageable);
        }

        List<EntryResponse> entries = result.getContent()
                .stream()
                .map(EntryResponse::from)
                .toList();

        return new PagedResponse<>(
                entries,
                result.getTotalElements(),
                page,
                limit,
                result.getTotalPages()
        );
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboard", key = "#userId"),
            @CacheEvict(value = "analytics", key = "#userId")
    })
    public EntryResponse createEntry(Long userId, EntryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        List<Tag> tags = resolveTags(userId, request.tagIds());

        Entry entry = Entry.builder()
                .user(user)
                .title(request.title())
                .content(request.content() != null ? request.content() : "")
                .mood(request.mood())
                .wordCount(countWords(request.content()))
                .tags(tags)
                .build();

        entryRepository.save(entry);
        metrics.incrementEntriesCreated();
        return EntryResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public EntryResponse getEntry(Long userId, Long entryId) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry", entryId));
        return EntryResponse.from(entry);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboard", key = "#userId"),
            @CacheEvict(value = "analytics", key = "#userId")
    })
    public EntryResponse updateEntry(Long userId, Long entryId, UpdateEntryRequest request) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry", entryId));

        if (request.title() != null) entry.setTitle(request.title());
        if (request.content() != null) {
            entry.setContent(request.content());
            entry.setWordCount(countWords(request.content()));
        }
        if (request.mood() != null) entry.setMood(request.mood());
        if (request.tagIds() != null) {
            entry.setTags(resolveTags(userId, request.tagIds()));
        }

        entryRepository.save(entry);
        return EntryResponse.from(entry);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dashboard", key = "#userId"),
            @CacheEvict(value = "analytics", key = "#userId")
    })
    public void deleteEntry(Long userId, Long entryId) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry", entryId));
        entryRepository.delete(entry);
    }

    @Transactional
    public EntryResponse toggleFavorite(Long userId, Long entryId) {
        Entry entry = entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry", entryId));
        entry.setFavorite(!entry.isFavorite());
        entryRepository.save(entry);
        return EntryResponse.from(entry);
    }

    private List<Tag> resolveTags(Long userId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();
        return tagRepository.findByUserIdOrderByName(userId)
                .stream()
                .filter(t -> tagIds.contains(t.getId()))
                .toList();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Arrays.stream(text.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .count();
    }

    private Mood parseMood(String mood) {
        try {
            return Mood.valueOf(mood.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mood value: " + mood);
        }
    }
}
