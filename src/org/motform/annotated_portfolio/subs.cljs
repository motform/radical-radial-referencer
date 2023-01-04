(ns org.motform.annotated-portfolio.subs
  (:require [clojure.set :as set]
            [re-frame.core :as rf]))

(def from-index (comp vals select-keys))
(defn from-state [k]
  (fn [db _]
    (-> db :db/state k)))

;; + functions return true if-not (seq s)
(defn +set-f [f]
  (fn [s x] (if (seq s) (f s x) true)))
(def +contains? (+set-f contains?))
(def +subset?  (+set-f set/subset?))

;; ? functions return false if-not (seq s)
(defn ?set-f [f]
  (fn [s x] (if (seq s) (f s x) false)))
(def ?subset? (?set-f set/subset?))

;; DB

(rf/reg-sub :db/project :db/project)
(rf/reg-sub :db/tags :db/tags)
(rf/reg-sub :db/references :db/references)

(rf/reg-sub
 :radial/complete?
 :<- [:db/references]
 :<- [:db/project]
 (fn [[references project] _]
   (and (seq references) (:reference/images project))))

;; dialog, reference, project

(rf/reg-sub :radial/delta (from-state :radial/delta))
(rf/reg-sub :radial/exported? (from-state :radial/exported?))
(rf/reg-sub :radial/playing? (from-state :radial/playing?))
(rf/reg-sub :reference/editing' (from-state :reference/editing))

(rf/reg-sub
 :reference/editing
 :<- [:db/references]
 :<- [:reference/editing']
 :-> (fn [[references editing]]
       (if (= :project editing) editing
           (references editing))))

;; Images

(rf/reg-sub :db/images :db/images)

(rf/reg-sub
 :images/for-reference
 :<- [:db/images]
 :=> from-index)

;; Tags

(rf/reg-sub :tags/active    (from-state :tags/active))
(rf/reg-sub :tags/highlight (from-state :tags/highlight))

(rf/reg-sub
 :tags/for-reference
 :<- [:db/tags]
 :=> from-index)

(rf/reg-sub
 :tag/inactive?
 :<- [:tags/active]
 :=> (complement +contains?))

(rf/reg-sub
 :tag/highlit?
 :<- [:tags/highlight]
 :=> contains?)

(rf/reg-sub
 :references/count
 :<- [:db/references]
 :-> count)

(rf/reg-sub
 :tags/active+highlight
 :<- [:tags/active]
 :<- [:tags/highlight]
 :-> (partial apply set/union))

(rf/reg-sub
 :tags/inactive?
 :<- [:tags/active+highlight]
 :=> (complement +subset?))

(rf/reg-sub
 :tags/highlit?
 :<- [:tags/highlight]
 :=> ?subset?)

(rf/reg-sub
 :tags/referenced
 :<- [:db/references]
 :-> #(->> % vals (map :reference/tags) (apply set/union)))

(rf/reg-sub
 :tags/in-use
 :<- [:db/tags]
 :<- [:tags/referenced]
 :-> (partial apply from-index))
