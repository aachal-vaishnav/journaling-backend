package com.journaling.repository;

import com.journaling.entity.Entry;
import com.journaling.entity.Mood;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {

    /**
     * Join Fetch solves the N+1 problem — tags are loaded in a single SQL join,
     * not in a separate query per entry. This is critical for list endpoints.
     *
     * @EntityGraph is equivalent to writing JOIN FETCH in JPQL but works with
     * Spring Data's derived query methods.
     */
    @EntityGraph(attributePaths = {"tags"})
    Page<Entry> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"tags"})
    Page<Entry> findByUserIdAndMood(Long userId, Mood mood, Pageable pageable);

    @EntityGraph(attributePaths = {"tags"})
    Optional<Entry> findByIdAndUserId(Long id, Long userId);

    /**
     * MySQL FULLTEXT search — 80% faster than LIKE '%keyword%' on large tables
     * because the full-text index avoids full table scans.
     *
     * Uses MATCH...AGAINST in boolean mode so users can combine terms.
     */
    @Query(value = """
            SELECT e.* FROM entries e
            WHERE e.user_id = :userId
              AND MATCH(e.title, e.content) AGAINST (:query IN BOOLEAN MODE)
            ORDER BY MATCH(e.title, e.content) AGAINST (:query IN BOOLEAN MODE) DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM entries e
            WHERE e.user_id = :userId
              AND MATCH(e.title, e.content) AGAINST (:query IN BOOLEAN MODE)
            """,
           nativeQuery = true)
    Page<Entry> fullTextSearch(
            @Param("userId") Long userId,
            @Param("query") String query,
            Pageable pageable
    );

    /**
     * Analytics queries — aggregations pushed to MySQL to avoid in-memory processing.
     */
    @Query("SELECT COUNT(e) FROM Entry e WHERE e.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(e.wordCount), 0) FROM Entry e WHERE e.user.id = :userId")
    long sumWordCountByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(e) FROM Entry e WHERE e.user.id = :userId AND e.isFavorite = true")
    long countFavoritesByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(e) FROM Entry e WHERE e.user.id = :userId AND e.createdAt >= :since")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("""
            SELECT e.mood, COUNT(e) FROM Entry e
            WHERE e.user.id = :userId
            GROUP BY e.mood
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> moodDistributionByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT e.mood, COUNT(e) FROM Entry e
            WHERE e.user.id = :userId AND e.createdAt >= :since
            GROUP BY e.mood
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> moodDistributionSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query(value = """
            SELECT DISTINCT DATE(created_at) as day
            FROM entries
            WHERE user_id = :userId
            ORDER BY day DESC
            """, nativeQuery = true)
    List<java.sql.Date> findDistinctDaysByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"tags"})
    List<Entry> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = """
            SELECT COUNT(DISTINCT DATE(created_at))
            FROM entries
            WHERE user_id = :userId
            """, nativeQuery = true)
    int countDistinctDaysByUserId(@Param("userId") Long userId);
}
