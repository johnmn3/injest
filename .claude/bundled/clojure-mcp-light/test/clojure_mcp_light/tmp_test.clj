(ns clojure-mcp-light.tmp-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure-mcp-light.tmp :as tmp]
            [babashka.fs :as fs]))

;; ============================================================================
;; Helper Function Tests
;; ============================================================================

(deftest sanitize-test
  (testing "replaces special characters with underscores"
    (is (= "hello_world" (tmp/sanitize "hello/world")))
    (is (= "foo_bar_baz" (tmp/sanitize "foo:bar:baz")))
    (is (= "test_123" (tmp/sanitize "test@123")))
    (is (= "a_b_c" (tmp/sanitize "a b c"))))

  (testing "preserves allowed characters"
    (is (= "file.txt" (tmp/sanitize "file.txt")))
    (is (= "my-file" (tmp/sanitize "my-file")))
    (is (= "file_name" (tmp/sanitize "file_name")))
    (is (= "abc123" (tmp/sanitize "abc123"))))

  (testing "collapses multiple underscores"
    (is (= "foo_bar" (tmp/sanitize "foo___bar")))
    (is (= "a_b" (tmp/sanitize "a____b")))
    (is (= "test_case" (tmp/sanitize "test//case"))))

  (testing "handles edge cases"
    (is (= "_" (tmp/sanitize "/")))
    (is (= "_" (tmp/sanitize "@#$%")))
    (is (string? (tmp/sanitize ""))))

  (testing "handles unicode characters"
    (is (= "_hello_" (tmp/sanitize "«hello»")))
    (is (= "_test_" (tmp/sanitize "→test←")))))

(deftest sha1-test
  (testing "produces consistent hashes"
    (let [input "test-string"
          hash1 (tmp/sha1 input)
          hash2 (tmp/sha1 input)]
      (is (= hash1 hash2))
      (is (= 40 (count hash1)))))

  (testing "produces different hashes for different inputs"
    (let [hash1 (tmp/sha1 "input1")
          hash2 (tmp/sha1 "input2")]
      (is (not= hash1 hash2))))

  (testing "produces valid hex strings"
    (let [hash (tmp/sha1 "test")]
      (is (re-matches #"[0-9a-f]{40}" hash))))

  (testing "handles empty strings"
    (let [hash (tmp/sha1 "")]
      (is (string? hash))
      (is (= 40 (count hash)))))

  (testing "produces known hash for test input"
    ;; SHA-1 of "hello" is known
    (is (= "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
           (tmp/sha1 "hello")))))

(deftest sha256-test
  (testing "produces consistent hashes"
    (let [input "test-string"
          hash1 (tmp/sha256 input)
          hash2 (tmp/sha256 input)]
      (is (= hash1 hash2))
      (is (= 64 (count hash1)))))

  (testing "produces different hashes for different inputs"
    (let [hash1 (tmp/sha256 "input1")
          hash2 (tmp/sha256 "input2")]
      (is (not= hash1 hash2))))

  (testing "produces valid hex strings"
    (let [hash (tmp/sha256 "test")]
      (is (re-matches #"[0-9a-f]{64}" hash))))

  (testing "handles empty strings"
    (let [hash (tmp/sha256 "")]
      (is (string? hash))
      (is (= 64 (count hash)))))

  (testing "produces known hash for test input"
    ;; SHA-256 of "hello" is known
    (is (= "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
           (tmp/sha256 "hello")))))

(deftest runtime-base-dir-test
  (testing "returns a valid directory path"
    (let [result (tmp/runtime-base-dir)]
      (is (string? result))
      (is (pos? (count result)))))

  (testing "returns either XDG_RUNTIME_DIR or tmpdir"
    (let [result (tmp/runtime-base-dir)
          tmpdir (System/getProperty "java.io.tmpdir")]
      ;; Result should be either XDG_RUNTIME_DIR (if set) or tmpdir
      (is (or (= result (System/getenv "XDG_RUNTIME_DIR"))
              (= result tmpdir))))))

(deftest editor-scope-id-test
  (testing "returns a string"
    (is (string? (tmp/editor-scope-id))))

  (testing "returns non-empty value"
    (is (pos? (count (tmp/editor-scope-id)))))

  (testing "returns valid session identifier"
    (let [result (tmp/editor-scope-id)]
      ;; Should be either gpid-based or "global"
      (is (or (str/starts-with? result "gpid-")
              (= result "global"))))))

(deftest project-root-path-test
  (testing "returns absolute path"
    (let [path (tmp/project-root-path)]
      (is (string? path))
      (is (fs/absolute? path))))

  (testing "returns normalized path"
    (let [path (tmp/project-root-path)]
      (is (= path (str (fs/normalize path)))))))

;; ============================================================================
;; Session Root Tests
;; ============================================================================

(deftest session-root-test
  (testing "generates session root with default context"
    (let [root (tmp/session-root {})]
      (is (string? root))
      (is (str/includes? root "clojure-mcp-light"))
      (is (str/includes? root "proj-"))))

  (testing "uses custom session-id when provided"
    (let [custom-session "my-custom-session"
          root (tmp/session-root {:session-id custom-session})]
      (is (str/includes? root custom-session))))

  (testing "uses custom project-root when provided"
    (let [custom-project "/custom/project/path"
          root (tmp/session-root {:project-root custom-project})
          expected-hash (tmp/sha1 custom-project)]
      (is (str/includes? root (str "proj-" expected-hash)))))

  (testing "produces consistent paths for same inputs"
    (let [ctx {:session-id "test-123" :project-root "/test/path"}
          root1 (tmp/session-root ctx)
          root2 (tmp/session-root ctx)]
      (is (= root1 root2))))

  (testing "produces different paths for different sessions"
    (let [root1 (tmp/session-root {:session-id "session-1"})
          root2 (tmp/session-root {:session-id "session-2"})]
      (is (not= root1 root2))))

  (testing "produces different paths for different projects"
    (let [root1 (tmp/session-root {:project-root "/project1"})
          root2 (tmp/session-root {:project-root "/project2"})]
      (is (not= root1 root2))))

  (testing "sanitizes session-id in path"
    (let [root (tmp/session-root {:session-id "my/special:session"})]
      (is (str/includes? root "my_special_session"))
      (is (not (str/includes? root "my/special:session"))))))

;; ============================================================================
;; Convenience Directory Tests
;; ============================================================================

(deftest backups-dir-test
  (testing "creates and returns backups directory"
    (let [ctx {:session-id "test-backup-session"
               :project-root "/test/backup/project"}
          backup-dir (tmp/backups-dir ctx)]
      (is (string? backup-dir))
      (is (str/ends-with? backup-dir "backups"))
      (is (fs/exists? backup-dir))
      (is (fs/directory? backup-dir))))

  (testing "is idempotent"
    (let [ctx {:session-id "test-backup-idempotent"
               :project-root "/test/backup/project2"}
          dir1 (tmp/backups-dir ctx)
          dir2 (tmp/backups-dir ctx)]
      (is (= dir1 dir2))
      (is (fs/exists? dir1)))))

(deftest nrepl-dir-test
  (testing "creates and returns nrepl directory"
    (let [ctx {:session-id "test-nrepl-session"
               :project-root "/test/nrepl/project"}
          nrepl-dir (tmp/nrepl-dir ctx)]
      (is (string? nrepl-dir))
      (is (str/ends-with? nrepl-dir "nrepl"))
      (is (fs/exists? nrepl-dir))
      (is (fs/directory? nrepl-dir))))

  (testing "is idempotent"
    (let [ctx {:session-id "test-nrepl-idempotent"
               :project-root "/test/nrepl/project2"}
          dir1 (tmp/nrepl-dir ctx)
          dir2 (tmp/nrepl-dir ctx)]
      (is (= dir1 dir2))
      (is (fs/exists? dir1)))))

;; ============================================================================
;; File Path Tests
;; ============================================================================

(deftest nrepl-session-file-test
  (testing "generates session file path"
    (let [ctx {:session-id "test-file-session"
               :project-root "/test/file/project"}
          file-path (tmp/nrepl-session-file ctx)]
      (is (string? file-path))
      (is (str/includes? file-path "nrepl"))
      (is (str/ends-with? file-path "session.edn"))))

  (testing "path is under nrepl directory"
    (let [ctx {:session-id "test-file-session2"
               :project-root "/test/file/project2"}
          file-path (tmp/nrepl-session-file ctx)
          nrepl-dir (tmp/nrepl-dir ctx)]
      (is (str/starts-with? file-path nrepl-dir)))))

(deftest backup-path-test
  (testing "generates hash-based backup path"
    (let [ctx {:session-id "test-backup-path"
               :project-root "/test/project"}
          source-file "/Users/bruce/test.clj"
          backup (tmp/backup-path ctx source-file)]
      (is (string? backup))
      (is (str/includes? backup "backups"))
      (is (str/ends-with? backup "test.clj"))))

  (testing "uses 2-level sharding structure"
    (let [ctx {:session-id "test-backup-structure"
               :project-root "/test/project"}
          source-file "/Users/bruce/test.clj"
          backup (tmp/backup-path ctx source-file)
          hash (tmp/sha256 (str (fs/absolutize (fs/normalize source-file))))
          shard1 (subs hash 0 2)
          shard2 (subs hash 2 4)]
      ;; Should contain shard directories
      (is (str/includes? backup (str "/" shard1 "/" shard2 "/")))
      ;; Should contain hash prefix in filename
      (is (str/includes? backup (str shard1 shard2)))))

  (testing "filename format is hash--basename"
    (let [ctx {:session-id "test-backup-format"
               :project-root "/test/project"}
          source-file "/Users/bruce/my-file.clj"
          backup (tmp/backup-path ctx source-file)
          filename (str (fs/file-name backup))]
      ;; Format: {hash}--{sanitized-basename}
      (is (str/includes? filename "--"))
      (is (str/ends-with? filename "--my-file.clj"))))

  (testing "produces consistent paths for same input"
    (let [ctx {:session-id "test-backup-consistent"
               :project-root "/test/project"}
          source-file "/Users/bruce/test.clj"
          backup1 (tmp/backup-path ctx source-file)
          backup2 (tmp/backup-path ctx source-file)]
      (is (= backup1 backup2))))

  (testing "produces different paths for different source files"
    (let [ctx {:session-id "test-backup-different"
               :project-root "/test/project"}
          backup1 (tmp/backup-path ctx "/Users/bruce/file1.clj")
          backup2 (tmp/backup-path ctx "/Users/bruce/file2.clj")]
      (is (not= backup1 backup2))))

  (testing "sanitizes special characters in filename"
    (let [ctx {:session-id "test-backup-special"
               :project-root "/test/project"}
          source-file "/Users/bruce/my file with spaces.clj"
          backup (tmp/backup-path ctx source-file)]
      (is (string? backup))
      ;; Spaces should be sanitized to underscores
      (is (str/ends-with? backup "my_file_with_spaces.clj"))
      (is (not (str/includes? backup " ")))))

  (testing "handles deep paths correctly"
    (let [ctx {:session-id "test-backup-deep"
               :project-root "/test/project"}
          source-file "/a/b/c/d/e/f/deep-file.clj"
          backup (tmp/backup-path ctx source-file)
          filename (str (fs/file-name backup))]
      ;; Should only have basename in filename, not full path
      (is (str/ends-with? backup "deep-file.clj"))
      ;; Should not contain intermediate directories a, b, c, d, e, f
      (is (not (str/includes? filename "/"))))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest integration-full-workflow-test
  (testing "full workflow creates proper directory structure"
    (let [ctx {:session-id "integration-test-session"
               :project-root "/test/integration/project"}
          root (tmp/session-root ctx)
          backups (tmp/backups-dir ctx)
          nrepl (tmp/nrepl-dir ctx)
          session-file (tmp/nrepl-session-file ctx)
          backup (tmp/backup-path ctx "/Users/test/file.clj")]

      ;; Verify all paths are strings
      (is (string? root))
      (is (string? backups))
      (is (string? nrepl))
      (is (string? session-file))
      (is (string? backup))

      ;; Verify directory structure
      (is (str/starts-with? backups root))
      (is (str/starts-with? nrepl root))
      (is (str/starts-with? session-file nrepl))
      (is (str/starts-with? backup backups))

      ;; Verify directories exist
      (is (fs/exists? backups))
      (is (fs/exists? nrepl))
      (is (fs/directory? backups))
      (is (fs/directory? nrepl)))))
