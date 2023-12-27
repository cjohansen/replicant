(ns replicant.protocols)

(defprotocol IRender
  :extend-via-metadata true

  (create-text-node [this text])
  (create-element [this tag-name options])

  (set-style [this el k v])
  (remove-style [this el k])

  (add-class [this el cn])
  (remove-class [this el cn])

  (set-attribute [this el a v opt])
  (remove-attribute [this el a])

  (set-event-handler [this el event handler])
  (remove-event-handler [this el event])

  (insert-before [this el child-node reference-node])
  (append-child [this el child-node])
  (remove-child [this el child-node])
  (replace-child [this el insert-child replace-child])

  (get-child [this el idx]))
