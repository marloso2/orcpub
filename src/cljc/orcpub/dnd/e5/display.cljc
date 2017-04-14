(ns orcpub.dnd.e5.display
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(def phb-url "https://www.amazon.com/gp/product/0786965606/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965606&linkId=9cd9647802c714f226bd591d61058143")

(def scag-url "https://www.amazon.com/gp/product/0786965800/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965800&linkId=9b93efa0fc7239ebbf005d0b17367233")

(def vgm-url "https://www.amazon.com/gp/product/0786966017/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966017&linkId=506a1b33174f884dcec5db8c6c07ad31")

(def sources
  {:phb {:abbr "PHB"
         :url phb-url}
   :vgm {:abbr "VGM"
         :url vgm-url}
   :scag {:abbr "SCAG"
          :url scag-url}})

(def plural-map
  {:feet :feet})

(defn unit-amount-description [{:keys [units amount singular plural] :or {amount 1 plural (plural-map units)}}]
  (str amount " " (if (not= 1 amount)
                    (if plural
                      (common/safe-name plural)
                      (str (common/safe-name units) "s"))
                    (if singular
                      (common/safe-name singular)
                      (str (common/safe-name units))))))

(defn source-description [source page]
  (str "see " (:abbr (sources (or source :phb))) " " page))

(defn frequency-description [{:keys [units amount] :or {amount 1}}]
  (str
   (case amount
     1 "once"
     2 "twice"
     (str amount " times"))
   "/"
   (s/replace (common/safe-name units) #"-" " ")))

(defn attack-description [{:keys [description attack-type area-type damage-type damage-die damage-die-count damage-modifier save save-dc page source] :as attack}]
  (str
   (if description (str description ", "))
   (case attack-type
     :area (case area-type
             :line (str (:line-width attack) " x " (:line-length attack) " ft. line, ")
             :cone (str (:length attack) " ft. cone, ")
             nil)
     :ranged "ranged, "
     "melee, ")
   damage-die-count "d" damage-die (if damage-modifier (common/mod-str damage-modifier))
   " "
   (if damage-type (common/safe-name damage-type))
   " damage"
   (if save (str ", DC" save-dc " " (common/safe-name save) " save"))
   (if page (str " (" (source-description source page) ")"))))

(defn action-description [{:keys [description summary source page duration range frequency]}]
  (str
   (or summary description)
   (if (or range duration frequency page)
     (str
      " ("
      (s/join ", "
              (remove
               nil?
               [(if range (str "range " (unit-amount-description range)))
                (if duration (str "lasts " (unit-amount-description duration)))
                (if frequency (str "use " (frequency-description frequency)))
                (if page (source-description source page))]))
      ")"))))
