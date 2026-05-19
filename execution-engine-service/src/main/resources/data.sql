-- =============================================================================
-- SEED DATA: Problem 1 — Mystic Realms Leaderboard (Stream API / EntrySet Sort)
--
-- Input format  (stdin):  line 1 = N, then N lines of "name score"
-- Output format (stdout): sorted names, one per line
--                         Primary:   score DESC
--                         Secondary: name   ASC
--
-- Re-runnable: ON CONFLICT (id) DO NOTHING keeps this idempotent.
-- =============================================================================

-- ─────────────────────────────────────────
-- SAMPLE TEST CASES (is_hidden = false)
-- Used by both RUN mode and SUBMIT mode
-- ─────────────────────────────────────────

-- TC 1: Basic 3-player case
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (1, 1, E'3\nAlice 100\nBob 150\nCharlie 80', E'Bob\nAlice\nCharlie', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 2: Two players share the same score
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (2, 1, E'3\nAlice 120\nBob 100\nCharlie 120', E'Alice\nCharlie\nBob', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 3: Mixed tie at top and middle
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (3, 1, E'4\nZoe 200\nAmy 150\nDavid 200\nBen 150', E'David\nZoe\nAmy\nBen', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 4: All players tied — full alphabetical sort
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (4, 1, E'3\nXavier 50\nYara 50\nWalter 50', E'Walter\nXavier\nYara', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 5: Single player
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (5, 1, E'1\nSoloPlayer 999', E'SoloPlayer', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 6: Zero scores with one winner
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (6, 1, E'3\nLoserA 0\nLoserB 0\nWinner 50', E'Winner\nLoserA\nLoserB', 3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 7: Lexicographic tie-break with numeric suffixes
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (7, 1,
  E'4\nplayer1 1000\nplayer2 1000\nplayer10 900\nplayer3 1000',
  E'player1\nplayer2\nplayer3\nplayer10',
  3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 8: Mixed alphanumeric names with ties at top
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (8, 1,
  E'4\nuser1 200\nuser10 250\nuser2 200\nuserA 250',
  E'user10\nuserA\nuser1\nuser2',
  3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 9: Six players, three distinct scores, multiple ties
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (9, 1,
  E'6\nA 10\nB 20\nC 10\nD 30\nE 20\nF 10',
  E'D\nB\nE\nA\nC\nF',
  3000, false)
ON CONFLICT (id) DO NOTHING;

-- TC 10: Underscore names, two score tiers
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (10, 1,
  E'6\nplayer_z 100\nplayer_a 150\nplayer_m 100\nplayer_x 150\nplayer_b 120\nplayer_k 120',
  E'player_a\nplayer_x\nplayer_b\nplayer_k\nplayer_m\nplayer_z',
  3000, false)
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────
-- HIDDEN TEST CASES (is_hidden = true)
-- Only executed in SUBMIT mode
-- ─────────────────────────────────────────

-- TC 11: Single player with large score
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (11, 1, E'1\nsingleplayer 750000', E'singleplayer', 3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 12: Two players equal score — pure alphabetical tie-break
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (12, 1, E'2\nalpha 500\nbeta 500', E'alpha\nbeta', 3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 13: Two players, max score difference
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (13, 1, E'2\nplayer_low 10\nplayer_high 1000', E'player_high\nplayer_low', 3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 14: All zero scores — purely alphabetical
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (14, 1,
  E'4\ngamma 0\ndelta 0\nepsilon 0\nzeta 0',
  E'delta\nepsilon\ngamma\nzeta',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 15: All at max score (1_000_000) — alphabetical only
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (15, 1,
  E'3\nmax_a 1000000\nmax_b 1000000\nmax_c 1000000',
  E'max_a\nmax_b\nmax_c',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 16: Full spread — max, mid, zero scores with ties in each tier
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (16, 1,
  E'5\nzero 0\nmax 1000000\nmid 500000\nanother_zero 0\nanother_max 1000000',
  E'another_max\nmax\nmid\nanother_zero\nzero',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 17: All equal scores, names with shared prefix and numeric suffix
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (17, 1,
  E'5\nu1 100\nu10 100\nu100 100\nu2 100\nu20 100',
  E'u1\nu10\nu100\nu2\nu20',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 18: Two score groups (p_* at 100, q_* at 50) — 10 players total
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (18, 1,
  E'10\np_a 100\np_b 100\np_c 100\np_d 100\np_e 100\nq_a 50\nq_b 50\nq_c 50\nq_d 50\nq_e 50',
  E'p_a\np_b\np_c\np_d\np_e\nq_a\nq_b\nq_c\nq_d\nq_e',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 19: Four players with nearly-max consecutive scores — strict descending
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (19, 1,
  E'4\nplayer_high 1000000\nplayer_mid_high 999999\nplayer_mid_low 999998\nplayer_low 999997',
  E'player_high\nplayer_mid_high\nplayer_mid_low\nplayer_low',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- TC 20: 50 players alternating score 100/99 — stress test for two-tier sort
INSERT INTO test_cases (id, problem_id, input, expected_output, timeout_ms, is_hidden)
VALUES (20, 1,
  E'50\np01 100\np02 99\np03 100\np04 99\np05 100\np06 99\np07 100\np08 99\np09 100\np10 99\np11 100\np12 99\np13 100\np14 99\np15 100\np16 99\np17 100\np18 99\np19 100\np20 99\np21 100\np22 99\np23 100\np24 99\np25 100\np26 99\np27 100\np28 99\np29 100\np30 99\np31 100\np32 99\np33 100\np34 99\np35 100\np36 99\np37 100\np38 99\np39 100\np40 99\np41 100\np42 99\np43 100\np44 99\np45 100\np46 99\np47 100\np48 99\np49 100\np50 99',
  E'p01\np03\np05\np07\np09\np11\np13\np15\np17\np19\np21\np23\np25\np27\np29\np31\np33\np35\np37\np39\np41\np43\np45\np47\np49\np02\np04\np06\np08\np10\np12\np14\np16\np18\np20\np22\np24\np26\np28\np30\np32\np34\np36\np38\np40\np42\np44\np46\np48\np50',
  3000, true)
ON CONFLICT (id) DO NOTHING;

-- Advance the sequence so future auto-inserts don't collide with seeded IDs
SELECT setval('test_cases_id_seq', 20);

