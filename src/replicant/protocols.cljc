(ns replicant.protocols)

(defprotocol IRender
  :extend-via-metadata true

  (create-text-node [this text])
  (create-element [this tag-name options])

  (set-style [this k v])
  (remove-style [this k])

  (add-class [this cn])
  (remove-class [this cn])

  (set-attribute [this a v opt])
  (remove-attribute [this a])

  (set-event-handler [this event handler])
  (remove-event-handler [this event])

  (insert-before [this child-node reference-node])
  (append-child [this child-node])
  (remove-child [this child-node])
  (replace-child [this insert-child replace-child])

  (get-child [this idx])
  (get-parent-node [this]))
