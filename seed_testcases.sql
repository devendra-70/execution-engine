-- ============================================================
-- PROBLEM 3: Mystic Realms Leaderboard (Stream API / EntrySet)
-- Input format  : line 1 = n, then n lines of "username score"
-- Output format : one username per line (ranked)
-- ============================================================

-- Sample test cases (is_hidden = false)
INSERT INTO test_cases (problem_id, input, expected_output, timeout_ms, is_hidden) VALUES
(3,
E'3\nAlice 100\nBob 150\nCharlie 80',
E'Bob\nAlice\nCharlie',
3000, false),

(3,
E'3\nAlice 120\nBob 100\nCharlie 120',
E'Alice\nCharlie\nBob',
3000, false),

(3,
E'4\nZoe 200\nAmy 150\nDavid 200\nBen 150',
E'David\nZoe\nAmy\nBen',
3000, false),

(3,
E'3\nXavier 50\nYara 50\nWalter 50',
E'Walter\nXavier\nYara',
3000, false),

(3,
E'1\nSoloPlayer 999',
E'SoloPlayer',
3000, false),

(3,
E'3\nLoserA 0\nLoserB 0\nWinner 50',
E'Winner\nLoserA\nLoserB',
3000, false),

(3,
E'4\nplayer1 1000\nplayer2 1000\nplayer10 900\nplayer3 1000',
E'player1\nplayer2\nplayer3\nplayer10',
3000, false),

(3,
E'4\nuser1 200\nuser10 250\nuser2 200\nuserA 250',
E'user10\nuserA\nuser1\nuser2',
3000, false),

(3,
E'6\nA 10\nB 20\nC 10\nD 30\nE 20\nF 10',
E'D\nB\nE\nA\nC\nF',
3000, false),

(3,
E'6\nplayer_z 100\nplayer_a 150\nplayer_m 100\nplayer_x 150\nplayer_b 120\nplayer_k 120',
E'player_a\nplayer_x\nplayer_b\nplayer_k\nplayer_m\nplayer_z',
3000, false);

-- Hidden test cases (is_hidden = true)
INSERT INTO test_cases (problem_id, input, expected_output, timeout_ms, is_hidden) VALUES
(3,
E'1\nsingleplayer 750000',
E'singleplayer',
3000, true),

(3,
E'2\nalpha 500\nbeta 500',
E'alpha\nbeta',
3000, true),

(3,
E'2\nplayer_low 10\nplayer_high 1000',
E'player_high\nplayer_low',
3000, true),

(3,
E'4\ngamma 0\ndelta 0\nepsilon 0\nzeta 0',
E'delta\nepsilon\ngamma\nzeta',
3000, true),

(3,
E'3\nmax_a 1000000\nmax_b 1000000\nmax_c 1000000',
E'max_a\nmax_b\nmax_c',
3000, true),

(3,
E'5\nzero 0\nmax 1000000\nmid 500000\nanother_zero 0\nanother_max 1000000',
E'another_max\nmax\nmid\nanother_zero\nzero',
3000, true),

(3,
E'5\nu1 100\nu10 100\nu100 100\nu2 100\nu20 100',
E'u1\nu10\nu100\nu2\nu20',
3000, true),

(3,
E'10\np_a 100\np_b 100\np_c 100\np_d 100\np_e 100\nq_a 50\nq_b 50\nq_c 50\nq_d 50\nq_e 50',
E'p_a\np_b\np_c\np_d\np_e\nq_a\nq_b\nq_c\nq_d\nq_e',
3000, true),

(3,
E'4\nplayer_high 1000000\nplayer_mid_high 999999\nplayer_mid_low 999998\nplayer_low 999997',
E'player_high\nplayer_mid_high\nplayer_mid_low\nplayer_low',
3000, true),

(3,
E'50\np01 100\np02 99\np03 100\np04 99\np05 100\np06 99\np07 100\np08 99\np09 100\np10 99\np11 100\np12 99\np13 100\np14 99\np15 100\np16 99\np17 100\np18 99\np19 100\np20 99\np21 100\np22 99\np23 100\np24 99\np25 100\np26 99\np27 100\np28 99\np29 100\np30 99\np31 100\np32 99\np33 100\np34 99\np35 100\np36 99\np37 100\np38 99\np39 100\np40 99\np41 100\np42 99\np43 100\np44 99\np45 100\np46 99\np47 100\np48 99\np49 100\np50 99',
E'p01\np03\np05\np07\np09\np11\np13\np15\np17\np19\np21\np23\np25\np27\np29\np31\np33\np35\np37\np39\np41\np43\np45\np47\np49\np02\np04\np06\np08\np10\np12\np14\np16\np18\np20\np22\np24\np26\np28\np30\np32\np34\np36\np38\np40\np42\np44\np46\np48\np50',
3000, true);


-- ============================================================
-- PROBLEM 1: Two Sum
-- Input format  : line 1 = n, line 2 = space-separated array,
--                 line 3 = target
-- Output format : two 0-based indices separated by a space
-- ============================================================

INSERT INTO test_cases (problem_id, input, expected_output, timeout_ms, is_hidden) VALUES
(1,
E'4\n2 7 11 15\n9',
E'0 1',
3000, false),

(1,
E'3\n3 2 4\n6',
E'1 2',
3000, false),

(1,
E'2\n3 3\n6',
E'0 1',
3000, false),

(1,
E'5\n1 2 3 4 5\n9',
E'3 4',
3000, false),

(1,
E'5\n-1 -2 -3 -4 -5\n-8',
E'2 4',
3000, false),

(1,
E'4\n0 4 3 0\n0',
E'0 3',
3000, false),

(1,
E'4\n1 5 3 2\n7',
E'1 3',
3000, false),

(1,
E'4\n2 5 5 11\n10',
E'1 2',
3000, false),

(1,
E'10\n1 2 3 4 5 6 7 8 9 10\n19',
E'8 9',
3000, false),

(1,
E'4\n100 200 300 400\n700',
E'2 3',
3000, false);

