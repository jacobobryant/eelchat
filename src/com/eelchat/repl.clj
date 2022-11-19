(ns com.eelchat.repl
  (:require [com.biffweb :as biff :refer [q]]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn seed-channels []
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (biff/submit-tx sys
      (for [[mem chan] (q db
                          '{:find [mem chan]
                            :where [[mem :mem/comm comm]
                                    [chan :chan/comm comm]]})]
        {:db/doc-type :message
         :msg/mem mem
         :msg/channel chan
         :msg/created-at :db/now
         :msg/text (str "Seed message " (rand-int 1000))}))))

(comment
  (seed-channels)

  ;; As of writing this, calling (biff/refresh) with Conjure causes stdout to
  ;; start going to Vim. fix-print makes sure stdout keeps going to the
  ;; terminal. It may not be necessary in your editor.
  (biff/fix-print (biff/refresh))

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull msg [*])
         :where [[msg :msg/text]]}))

  (sort (keys @biff/system))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"})))
