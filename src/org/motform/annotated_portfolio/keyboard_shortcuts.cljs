(ns org.motform.annotated-portfolio.keyboard-shortcuts
  (:require [re-frame.core :as rf]))

(defn non-reference-target? [event]
  (#{"screenshot" "radial" "radial-container"} (.. event -target -id)))

(defn unfocus-reference [event]
  (when (non-reference-target? event)
    (rf/dispatch [:reference/editing nil])))

(defn undo [] (rf/dispatch [:undo]))

(defn redo [] (rf/dispatch [:redo]))

(defn on-body? [event]
  (= (.-body js/document) (.-target event)))

(defmulti global identity)

(defmethod global :platform/mac [_]
  (fn [event]
    (let [cmd? (.-metaKey event)
          shift? (.-shiftKey event)]
      (case (.. event -key)
        "Escape" (rf/dispatch [:reference/editing nil])
        ("Backspace" "Delete") (when (on-body? event) (rf/dispatch [:reference/delete]))
        ;; ("n" "N") (when (and cmd? (on-body? event)) (rf/dispatch [:reference/new]))
        ("z" "Z") (when cmd? (if shift? (redo) (undo)))
        nil))))

(defmethod global :platform/pc [_]
  (fn [event]
    (let [ctrl? (.-ctrlKey event)]
      (case (.. event -key)
        "Escape" (rf/dispatch [:reference/editing nil])
        ("Backspace" "Delete") (when (on-body? event) (rf/dispatch [:reference/delete]))
        ("z" "Z") (when ctrl? (undo))
        ("y" "Y") (when ctrl? (redo))
        nil))))

(defmethod global :default [platform]
  (.error js/console (str "Error in handle-global-keyboard-shortcuts, unknown platform " platform)))

