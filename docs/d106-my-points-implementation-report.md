# D106 My points implementation evidence

2026-10-20. Staged implementation in task/d106-stage0-overlay-registry.
OWNER approval commit: 8329291. No push or Samsung deployment authorized.
Stage1/H05 OPEN; unified Backup/Restore and Android Auto Backup untouched.

## Stage A — data

Room 2.8.4; additive schema 16 to 17, user_points and user_point_photos.
Point and photo metadata create/update share a transaction. Creation forces
is_visible=true in the same row. Editing preserves saved visibility and creation
facts. All reads and changes are Territory scoped; point deletion cascades only
its photo rows, and Territory deletion is blocked while it owns points.
Managed-file cleanup is not implemented or claimed at this stage.

Current run: offline connectedDebugAndroidTest on emulator-5558/API37, explicitly
isolated with ANDROID_SERIAL; 21/21 PASS (UserPointMigrationTest 1,
UserPointRepositoryTest 6, GpxDeletionMigrationTest 1,
RoomPhysicalObjectRepositoryTest 13). UserPointValidationTest JVM 2/2 PASS.
MigrationTestHelper validates schema and compares all legacy table row sets in
the fixture; seeded Territory, Observer, physical object and READY GPX survive,
GPX structural and deletion triggers pass verification; foreign_key_check empty.
This does not qualify a physical-device migration or unseeded historical states.

The earlier saved 18/20 run preceded existing fixture corrections; current
21/21 result supersedes it. No GPX production code changed.

External research: official Room migration/testing documentation and installed
2.8.4 test/runtime artifacts; existing explicit Migration and MigrationTestHelper
mechanisms retained. https://developer.android.com/training/data-storage/room/migrating-db-versions

## Pending

Stages B-D, UI screenshots, full build/lint and final qualification pending.
No My points runtime UI or Samsung PASS is claimed.
Pre-existing Samsung reports and dirty handoff remain outside task commits.
