(ns org.motform.annotated-portfolio.events
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require ["html2canvas" :as html2canvas]
            [cljs.core.async :as async]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs.reader :as reader]
            [day8.re-frame.undo :as undo]
            [re-frame.core :as rf]
            [org.motform.annotated-portfolio.db :as db]
            [org.motform.annotated-portfolio.components.utils :as utils]))

;;; Utils 

(defn index-by [k xs]
  (reduce (fn [m x] (assoc m (get x k) x)) {} xs))

(defn download-file [data-URL file-name]
  (let [anchor (doto (.createElement js/document "a")
                 (-> .-href (set! data-URL))
                 (-> .-download (set! file-name)))]
    (.click anchor)
    (.revokeObjectURL js/URL data-URL)))

;;; Interceptors 

(defn assoc-local-storage [k v]
  (.setItem js/localStorage k (str v)))

(defn get-local-storage [k]
  (when-let [item (.getItem js/localStorage k)]
    (reader/read-string item)))

(def local-storage-key "annotated-portfolio-app-db")

(defn collections->local-storage [{:db/keys [images] :as db}]
  (assoc-local-storage local-storage-key (dissoc db :db/state :db/images))
  (doseq [image images]
    (apply assoc-local-storage image)))

(rf/reg-cofx
 :local-storage
 (fn [cofx _]
   (let [{:db/keys [references project] :as db} (get-local-storage local-storage-key)
         images (->> (conj (vals references) project)
                     (mapcat :reference/images)
                     (map get-local-storage)
                     (index-by :image/id))]
     (assoc cofx :local-storage (assoc db :db/images images)))))

(def local-storage-interceptor [(rf/after collections->local-storage)])

;;; DB

(rf/reg-event-fx
 :db/initialize
 [(rf/inject-cofx :local-storage)]
 (fn [{:keys [local-storage]} [_ default-db]]
   (let [saved-db (if local-storage (dissoc local-storage :db/state) {})]
     {:db (merge default-db saved-db)})))

;;; State

(rf/reg-fx
 ::reset!
 (fn reset-state []
   (.clear (.-localStorage js/window))))

(rf/reg-event-fx
 :state/reset!
 [local-storage-interceptor (undo/undoable "Reset stat")]
 (fn [_ _]
   {::reset! nil
    :db db/default}))

(rf/reg-fx
 :export-portfolio
 (fn export-portfolio [{:keys [portfolio project-name]}]
   (let [blob (js/Blob. #js [(prn-str portfolio)] #js {:type "application/edn"})
         file-name (str project-name "-" (.getTime (js/Date.)) ".radial")
         data-URL (.createObjectURL js/URL blob)]
     (download-file data-URL file-name))))

(rf/reg-event-fx
 :state/export
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:db/state :radial/exported?] true)
    :export-portfolio {:portfolio (dissoc db :db/state)
                       :project-name (get-in db [:db/project :reference/name])}}))

(defn read-edn! [file]
  (let [result (async/promise-chan)]
    (try (let [reader (js/FileReader.)]
           (.addEventListener reader "load"
                              #(do (->> (.-result reader)
                                        reader/read-string
                                        (async/put! result))
                                   (async/close! result)))
           (.readAsText reader file))
         (catch js/Object error
           (async/put! result {:error error})
           (async/close! result)))
    result))

(rf/reg-fx
 ::read-edn
 (fn read-edn [{:keys [file on-success on-error]}]
   (go
     (let [result (async/<! (read-edn! file))]
       (if (:error result)
         (rf/dispatch (conj on-error result))
         (rf/dispatch (conj on-success result)))))))

(rf/reg-event-fx
 :state/upload
 (fn [_ [_ file]]
   {::read-edn {:file file
                :on-success [:state/import]
                :on-error   [:file/upload-error]}}))

(rf/reg-event-db
 :state/import
 [local-storage-interceptor (undo/undoable "Importing state.")]
 (fn [db [_ import]]
   (-> db
       (merge import)  ;; If we did a deep merge, we could preseve any added references!
       (assoc-in [:db/state :radial/exported?] true))))

;;; Screenshot

(defn canvas-screenshot []
  (go
    (let [screenshot-target (js/document.querySelector "#screenshot")
          canvas (<p! (html2canvas. screenshot-target))]
      (.toDataURL canvas "image/png"))))

(rf/reg-fx
 ::screenshot
 (fn screenshot [_]
   (go
     (let [data-URL (async/<! (canvas-screenshot))
           file-name (str "annotated-portfolio-" (.getTime (js/Date.)) ".png")]
       (download-file data-URL file-name)))))

(rf/reg-event-fx
 :screenshot/take
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:db/state :radial/exported?] true)
    ::screenshot nil}))

;;; Tags

(rf/reg-event-db
 :tag/add
 [local-storage-interceptor (undo/undoable "Adding tag.")]
 (fn [db [_ {:tag/keys [id] :as new-tag}]]
   (assoc-in db [:db/tags id] new-tag)))

(rf/reg-event-db
 :tag/delete
 [local-storage-interceptor (undo/undoable "Deleting tag.")]
 (fn [db [_ id]]
   (update db :db/tags dissoc id)))

(rf/reg-event-db
 :tags.active/toggle
 (fn [db [_ tag-id]]
   (update-in db [:db/state :tags/active] utils/toggle tag-id)))

(rf/reg-event-db
 :tags.active/clear
 (fn [db _]
   (assoc-in db [:db/state :tags/active] #{})))

(rf/reg-event-db
 :tags/highlight
 (fn [db [_ tag-id]]
   (update-in db [:db/state :tags/highlight] conj tag-id)))

(rf/reg-event-db
 :tags/remove-highlight
 (fn [db [_ tag-id]]
   (update-in db [:db/state :tags/highlight] disj tag-id)))

;;; Files

;; Adapted from: https://github.com/jtkDvlp/re-frame-readfile-fx
(defn read-file-as-data-URL! [file]
  (let [result (async/promise-chan)]
    (try (let [meta #:image{:name (.-name file)
                            :size (.-size file)
                            :type (.-type file)
                            :id   (random-uuid)}
               reader (js/FileReader.)]
           (.addEventListener reader "load"
                              #(do (->> (.-result reader)
                                        (assoc meta :image/data-URL)
                                        (async/put! result))
                                   (async/close! result)))
           (.readAsDataURL reader file))
         (catch js/Object error
           (async/put! result {:error error :file file})
           (async/close! result)))
    result))

(rf/reg-fx
 ::read-image-file
 (fn read-image-file-fx [{:keys [files on-success on-error]}]
   (go
     (let [result (->> (mapv read-file-as-data-URL! files)
                       (async/map vector)
                       (async/<!))]
       (if (some :error result)
         (rf/dispatch (conj on-error result))
         (rf/dispatch (conj on-success result)))))))

(rf/reg-event-fx
 :project/image-upload
 (fn [_ [_ _ files]]
   {::read-image-file {:files files
                       :on-success [:project/handle-image-upload]
                       :on-error   [:file/upload-error]}}))

(rf/reg-event-fx
 :reference/image-upload
 (fn [_ [_ id files]]
   {::read-image-file {:files files
                       :on-success [:reference/handle-image-upload id]
                       :on-error   [:file/upload-error]}}))

(rf/reg-event-db
 :reference/handle-image-upload
 [local-storage-interceptor (undo/undoable "Uploading Project")]
 (fn [db [_ id images]]
   (let [image-ids (mapv :image/id images)
         image-index (index-by :image/id images)
         old-image-ids (get-in db [:db/references id :reference/images])]
     (-> db
         (update :db/images #(apply dissoc % old-image-ids))
         (update :db/images merge image-index)
         (assoc-in [:db/references id :reference/images] image-ids)
         (assoc-in [:db/references id :reference/active-image] (first image-ids))))))

(rf/reg-event-db
 :project/handle-image-upload
 [local-storage-interceptor (undo/undoable "Adding project.")]
 (fn [db [_ images]]
   (let [image-ids (mapv :image/id images)
         image-index (index-by :image/id images)]
     (-> db
         (assoc-in [:db/project :reference/images] image-ids)
         (update :db/images merge image-index)))))

(rf/reg-event-db
 :project/edit
 [local-storage-interceptor]
 (fn [db [_ k v]]
   (assoc-in db [:db/project k] v)))

(rf/reg-event-db
 :reference/handle-upload
 [local-storage-interceptor (undo/undoable "Uploading Project")]
 (fn [db [_ {:reference/keys [id] :as reference} images]]
   (let [image-ids (mapv :image/id images)
         image-index (index-by :image/id images)
         reference (assoc reference :reference/images image-ids)]
     (-> db
         (update :db/references assoc id reference)
         (update :db/images merge image-index)
         (assoc-in [:db/state :dialog/open] nil)))))

(rf/reg-event-db
 :file/upload-error
 (fn [db [_ error]]
   (.warn js/console (str error))
   (-> db
       (assoc-in [:db/state :db/errors :error/file-upload] error)
       (assoc-in [:db/state :dialog/open] nil))))

(defn reference-template []
  #:reference{:id (random-uuid)
              :tags #{}
              :timestamp (.now js/Date)
              :version :version/alpha
              :type :media/images
              :name ""
              :images nil
              :reference/weight 1})

(rf/reg-event-db
 :reference/new
 [local-storage-interceptor (undo/undoable "Add reference.")]
 (fn [db _]
   (let [{:reference/keys [id] :as reference} (reference-template)]
     (-> db
         (assoc-in [:db/references id] reference)
         (assoc-in [:db/state :reference/editing] id)))))

(rf/reg-event-db
 :reference/editing
 (fn [db [_ reference-id]]
   (assoc-in db [:db/state :reference/editing] reference-id)))

(rf/reg-event-db
 :reference/edit
 [local-storage-interceptor #_(undo/undoable "Editing reference.")]
 (fn [db [_ reference-id k v]]
   (assoc-in db [:db/references reference-id k] v)))

(rf/reg-event-db
 :reference/toggle-tag
 [local-storage-interceptor (undo/undoable "Toggle tag.")]
 (fn [db [_ reference-id tag-id]]
   (update-in db [:db/references reference-id :reference/tags] utils/toggle tag-id)))

(rf/reg-event-db
 :reference/update
 [local-storage-interceptor (undo/undoable "Editing reference.")]
 (fn [db [_ {:reference/keys [id] :as reference}]]
   (-> db
       (update-in [:db/references id] merge reference)
       (assoc-in [:db/state :dialog/open] nil)
       (assoc-in [:db/state :reference/editing] nil))))

(rf/reg-event-db
 :reference/delete
 [local-storage-interceptor (undo/undoable "Deleting reference.")]
 (fn [db [_ {:reference/keys [id images]}]]
   (let [id (or id (get-in db [:db/state :reference/editing]))
         images (or images (get-in db [:db/references id :reference/images]))]
     (-> db
         (update :db/images #(apply dissoc % images))
         (update :db/references dissoc id)
         (assoc-in [:db/state :dialog/open] nil)
         (assoc-in [:db/state :reference/editing] nil)))))

(rf/reg-event-db
 :radial/tick
 (fn [db _]
   (if (get-in db [:db/state :radial/playing?])
     (update-in db [:db/state :radial/delta] #(-> % inc #_(mod 361)))
     db)))

(rf/reg-event-db
 :radial/delta
 (fn [db [_ delta]]
   (assoc-in db [:db/state :radial/delta] delta)))

(rf/reg-event-db
 :radial/play
 (fn [db _]
   (assoc-in db [:db/state :radial/playing?] (not (get-in db [:db/state :radial/playing?])))))

(rf/reg-event-db
 :radial/exported?
 (fn [db _]
   (if-not (and (-> db :db/references seq)
                (-> db :db/project :reference/images))
     (assoc-in db [:db/state :radial/exported?] false)
     db)))
