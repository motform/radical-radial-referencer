(ns org.motform.annotated-portfolio.db)

(def default
  {:db/images     {}
   :db/project    #:reference{:id (random-uuid) :timestamp (.now js/Date) :name "" :images nil}
   :db/references {}
   :db/tags       {}
   :db/state {:tags/active       #{}
              :tags/highlight    #{}
              :radial/delta      0
              :radial/playing?   false
              :radial/exported?  true
              :reference/editing nil
              :project/editing?  false}})
