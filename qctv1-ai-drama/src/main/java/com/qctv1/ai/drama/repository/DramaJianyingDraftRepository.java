package com.qctv1.ai.drama.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.ai.drama.domain.DramaExportPackageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DramaJianyingDraftRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DramaJianyingDraftRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long createEditTask(Long aiTaskId, Long seriesId, Long episodeId, String rootPath) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO drama_edit_task
                    (ai_task_id, series_id, episode_id, status, progress, stage, root_path, created_at, updated_at)
                    VALUES (?, ?, ?, 'PENDING', 0, 'QUEUED', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, aiTaskId);
            ps.setLong(2, seriesId);
            ps.setLong(3, episodeId);
            ps.setString(4, rootPath);
            ps.setObject(5, now);
            ps.setObject(6, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateEditTask(Long editTaskId, String status, int progress, String stage, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE drama_edit_task
                SET status = ?, progress = ?, stage = ?, error_message = ?, updated_at = ?
                WHERE id = ?
                """, status, progress, stage, errorMessage, LocalDateTime.now(), editTaskId);
    }

    public void updateEditTaskRootPath(Long editTaskId, String rootPath) {
        jdbcTemplate.update("""
                UPDATE drama_edit_task
                SET root_path = ?, updated_at = ?
                WHERE id = ?
                """, rootPath, LocalDateTime.now(), editTaskId);
    }

    public Long upsertLicense(String provider, String licenseName, String licenseUrl, String sourceUrl, String localRecordPath) {
        List<Long> existing = jdbcTemplate.query("""
                        SELECT id FROM drama_material_license
                        WHERE provider = ? AND license_name = ? AND IFNULL(source_url, '') = IFNULL(?, '')
                        ORDER BY id DESC LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                provider, licenseName, sourceUrl);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO drama_material_license
                    (provider, license_name, license_url, source_url, local_record_path, downloaded_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, provider);
            ps.setString(2, licenseName);
            ps.setString(3, licenseUrl);
            ps.setString(4, sourceUrl);
            ps.setString(5, localRecordPath);
            ps.setObject(6, now);
            ps.setObject(7, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void upsertMaterialAsset(
            String category,
            String name,
            String provider,
            String sourceUrl,
            Long licenseId,
            Map<String, Object> tags,
            Double durationSeconds,
            String contentHash,
            String localPath
    ) {
        jdbcTemplate.update("""
                INSERT INTO drama_material_asset
                (category, name, provider, source_url, license_id, tags, duration_seconds, content_hash, local_path, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, 'READY', ?, ?)
                ON DUPLICATE KEY UPDATE
                    category = VALUES(category),
                    name = VALUES(name),
                    provider = VALUES(provider),
                    source_url = VALUES(source_url),
                    license_id = VALUES(license_id),
                    tags = VALUES(tags),
                    duration_seconds = VALUES(duration_seconds),
                    local_path = VALUES(local_path),
                    status = 'READY',
                    updated_at = VALUES(updated_at)
                """,
                category,
                name,
                provider,
                sourceUrl,
                licenseId,
                json(tags),
                durationSeconds == null ? null : BigDecimal.valueOf(durationSeconds),
                contentHash,
                localPath,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    public List<Map<String, Object>> listReadyMaterials(String category, int limit) {
        return jdbcTemplate.queryForList("""
                        SELECT id, category, name, provider, source_url, tags, duration_seconds, local_path
                        FROM drama_material_asset
                        WHERE category = ? AND status = 'READY'
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                category,
                Math.max(1, Math.min(limit, 100)));
    }

    public void insertShotAnalysis(
            Long editTaskId,
            Long episodeId,
            Long shotId,
            Integer shotNo,
            Double startTime,
            Double endTime,
            List<String> framePaths,
            Map<String, Object> analysis,
            String modelName,
            Double confidence,
            String rawResponse
    ) {
        jdbcTemplate.update("""
                INSERT INTO drama_shot_analysis
                (edit_task_id, episode_id, shot_id, shot_no, start_time, end_time, frame_paths,
                 characters, visible_actions, objects, emotion, inferred_action, dialogue_anchors,
                 model_name, confidence, raw_response, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON),
                        CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?, CAST(? AS JSON),
                        ?, ?, ?, ?)
                """,
                editTaskId,
                episodeId,
                shotId,
                shotNo,
                decimal(startTime),
                decimal(endTime),
                json(framePaths),
                json(analysis.get("characters")),
                json(analysis.get("visibleActions")),
                json(analysis.get("objects")),
                stringValue(analysis.get("emotion")),
                stringValue(analysis.get("inferredAction")),
                json(analysis.get("dialogueAnchors")),
                modelName,
                confidence == null ? null : BigDecimal.valueOf(confidence),
                rawResponse,
                LocalDateTime.now());
    }

    public void insertDialogueTimeline(
            Long editTaskId,
            Long episodeId,
            Long shotId,
            int cueNo,
            String speaker,
            String text,
            double startTime,
            double endTime,
            String actionAnchor,
            String voiceModel,
            String voiceClipPath
    ) {
        jdbcTemplate.update("""
                INSERT INTO drama_dialogue_timeline
                (edit_task_id, episode_id, shot_id, cue_no, speaker, text, start_time, end_time,
                 action_anchor, voice_model, voice_clip_path, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                editTaskId,
                episodeId,
                shotId,
                cueNo,
                speaker,
                text,
                decimal(startTime),
                decimal(endTime),
                actionAnchor,
                voiceModel,
                voiceClipPath,
                LocalDateTime.now());
    }

    public Long createExportPackage(
            Long editTaskId,
            Long episodeId,
            String referenceVideoPath,
            String cleanVideoPath,
            String voiceMixPath,
            String voiceClipsDir,
            String bgmSfxPath,
            String subtitleSrtPath,
            String danmakuCsvPath,
            String packageZipPath,
            String manifestPath
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO drama_export_package
                    (edit_task_id, episode_id, reference_video_path, clean_video_path, voice_mix_path,
                     voice_clips_dir, bgm_sfx_path, subtitle_srt_path, danmaku_csv_path,
                     package_zip_path, manifest_path, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, editTaskId);
            ps.setLong(2, episodeId);
            ps.setString(3, referenceVideoPath);
            ps.setString(4, cleanVideoPath);
            ps.setString(5, voiceMixPath);
            ps.setString(6, voiceClipsDir);
            ps.setString(7, bgmSfxPath);
            ps.setString(8, subtitleSrtPath);
            ps.setString(9, danmakuCsvPath);
            ps.setString(10, packageZipPath);
            ps.setString(11, manifestPath);
            ps.setObject(12, LocalDateTime.now());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public Optional<DramaExportPackageRecord> findLatestPackage(Long episodeId) {
        List<DramaExportPackageRecord> records = jdbcTemplate.query("""
                        SELECT * FROM drama_export_package
                        WHERE episode_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                packageMapper(),
                episodeId);
        return records.stream().findFirst();
    }

    public Optional<DramaExportPackageRecord> findPackage(Long packageId) {
        List<DramaExportPackageRecord> records = jdbcTemplate.query("""
                        SELECT * FROM drama_export_package
                        WHERE id = ?
                        LIMIT 1
                        """,
                packageMapper(),
                packageId);
        return records.stream().findFirst();
    }

    private RowMapper<DramaExportPackageRecord> packageMapper() {
        return (rs, rowNum) -> new DramaExportPackageRecord(
                rs.getLong("id"),
                rs.getLong("edit_task_id"),
                rs.getLong("episode_id"),
                rs.getString("reference_video_path"),
                rs.getString("clean_video_path"),
                rs.getString("voice_mix_path"),
                rs.getString("voice_clips_dir"),
                rs.getString("bgm_sfx_path"),
                rs.getString("subtitle_srt_path"),
                rs.getString("danmaku_csv_path"),
                rs.getString("package_zip_path"),
                rs.getString("manifest_path"),
                rs.getObject("created_at", LocalDateTime.class)
        );
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
