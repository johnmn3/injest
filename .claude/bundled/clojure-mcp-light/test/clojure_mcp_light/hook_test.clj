(ns clojure-mcp-light.hook-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp-light.hook :as hook]
            [babashka.fs :as fs]))

(deftest clojure-file?-test
  (testing "identifies Clojure files by extension"
    (is (hook/clojure-file? "test.clj"))
    (is (hook/clojure-file? "test.cljs"))
    (is (hook/clojure-file? "test.cljc"))
    (is (hook/clojure-file? "test.cljd"))
    (is (hook/clojure-file? "test.bb"))
    (is (hook/clojure-file? "test.lpy"))
    (is (hook/clojure-file? "config.edn")))

  (testing "case-insensitive extension matching"
    (is (hook/clojure-file? "test.CLJ"))
    (is (hook/clojure-file? "test.CLJS"))
    (is (hook/clojure-file? "test.CLJD"))
    (is (hook/clojure-file? "test.EDN"))
    (is (hook/clojure-file? "test.LPY")))

  (testing "identifies files with Babashka shebang"
    (let [temp-file (str (fs/create-temp-file {:prefix "test-bb-" :suffix ".sh"}))]
      (try
        (spit temp-file "#!/usr/bin/env bb\n(println \"hello\")" :encoding "UTF-8")
        (is (hook/clojure-file? temp-file))
        (finally
          (fs/delete-if-exists temp-file))))

    (let [temp-file (str (fs/create-temp-file {:prefix "test-bb-" :suffix ".sh"}))]
      (try
        (spit temp-file "#!/usr/bin/bb\n(println \"hello\")" :encoding "UTF-8")
        (is (hook/clojure-file? temp-file))
        (finally
          (fs/delete-if-exists temp-file))))

    (let [temp-file (str (fs/create-temp-file {:prefix "test-bb-" :suffix ".sh"}))]
      (try
        (spit temp-file "#!/usr/local/bin/bb --nrepl-server 1667\n(println \"hello\")" :encoding "UTF-8")
        (is (hook/clojure-file? temp-file))
        (finally
          (fs/delete-if-exists temp-file)))))

  (testing "rejects files without Babashka shebang"
    (let [temp-file (str (fs/create-temp-file {:prefix "test-bash-" :suffix ".sh"}))]
      (try
        (spit temp-file "#!/bin/bash\necho \"hello\"" :encoding "UTF-8")
        (is (nil? (hook/clojure-file? temp-file)))
        (finally
          (fs/delete-if-exists temp-file)))))

  (testing "rejects non-Clojure files"
    (is (nil? (hook/clojure-file? "test.js")))
    (is (nil? (hook/clojure-file? "test.py")))
    (is (nil? (hook/clojure-file? "README.md")))
    (is (nil? (hook/clojure-file? "package.json"))))

  (testing "handles nil file path"
    (is (nil? (hook/clojure-file? nil))))

  (testing "handles non-existent file without error"
    (is (nil? (hook/clojure-file? "/nonexistent/file.xyz")))))

(deftest process-hook-test
  (testing "allows non-Clojure files through unchanged"
    (let [hook-input {:hook_event_name "PreToolUse"
                      :tool_name "Write"
                      :tool_input {:file_path "test.js"
                                   :content "console.log('hello')"}}
          result (hook/process-hook hook-input)]
      (is (nil? result))))

  (testing "allows valid Clojure code through unchanged"
    (let [hook-input {:hook_event_name "PreToolUse"
                      :tool_name "Write"
                      :tool_input {:file_path "test.clj"
                                   :content "(def x 1)"}}
          result (hook/process-hook hook-input)]
      (is (nil? result))))

  (testing "fixes delimiter errors in Write operations"
    (let [hook-input {:hook_event_name "PreToolUse"
                      :tool_name "Write"
                      :tool_input {:file_path "test.clj"
                                   :content "(def x 1"}}
          result (hook/process-hook hook-input)]
      (is (map? result))
      (is (= "(def x 1)"
             (get-in result [:hookSpecificOutput :updatedInput :content])))))

  (testing "allows Edit operations for Clojure files"
    (let [hook-input {:hook_event_name "PreToolUse"
                      :tool_name "Edit"
                      :tool_input {:file_path "test.clj"
                                   :old_string "(def x 1)"
                                   :new_string "(def x 2)"}
                      :session_id "test-session"}
          result (hook/process-hook hook-input)]
      (is (nil? result)))))
