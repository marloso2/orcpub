(ns orcpub.dnd.e5.views
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.common :as common]
            [orcpub.entity :as entity]
            [orcpub.components :as comps]
            [orcpub.entity-spec :as es]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.subs :as subs]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.units :as units]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.character.random :as char-random]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [cljs.pprint :refer [pprint]]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.template :as t]
            [orcpub.template :as template]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.events :as events]
            [clojure.string :as s]
            [orcpub.user-agent :as user-agent]
            [cljs.core.async :refer [<! timeout]]
            [bidi.bidi :as bidi])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def text-color "#484848")

(def orange "#f0a100")

(def input-style
  {:height "38px"
   :border-style "solid"
   :border-width "1px"
   :border-radius "3px"
   :font-size "14px"
   :padding-left "10px"
   :color text-color})

(def default-input-style
  (merge
   input-style
   {:border-color "rgba(72,72,72,0.37)"}))

(defn event-value [e]
  (.. e -target -value))

(defn set-value [atom key e]
  (swap! atom assoc key (event-value e)))

(defn validation-messages [messages]
  (if messages
    [:ul.t-a-l.p-l-20.p-r-20.m-b-10
     (doall
      (map-indexed
       (fn [i msg]
         ^{:key i}
         [:li.red (str common/dot-char " " msg)])
       messages))]))

(defn base-input [attrs]
  [:div.m-b-10
   [:div.f-s-10.t-a-l.m-l-10 (:placeholder attrs)]
   [:div.flex.p-l-10.p-l-10.p-r-10
    [:input.flex-grow-1
     (merge
      attrs
      {:auto-complete :off})]]])

(defn form-input []
  (let [blurred? (r/atom false)]
    (fn [{:keys [title key value messages type on-change]}]
      [:div
       [base-input
        {:name key
         :type type
         :value value
         :placeholder title
         :style input-style
         :class-name (if (and @blurred? (seq messages))
                       "b-red"
                       "b-gray")
         :on-focus (fn [_] (reset! blurred? false))
         :on-change on-change
         :on-blur (fn [e] (reset! blurred? true))}]
       (if @blurred? (validation-messages messages))])))

(defn export-pdf [built-char & [options]]
  (fn [_]
    (let [field (.getElementById js/document "fields-input")]
      (aset field "value" (str (pdf-spec/make-spec built-char options)))
      (.submit (.getElementById js/document "download-form")))))

(defn download-form [built-char]
  [:form.download-form
   {:id "download-form"
    :action (if (s/starts-with? js/window.location.href "http://localhost")
              "http://localhost:8890/character.pdf"
              "/character.pdf")
    :method "POST"
    :target "_blank"}
   [:input {:type "hidden" :name "body" :id "fields-input"}]])

(defn svg-icon [icon-name & [size theme-override]]
  (let [theme (or theme-override @(subscribe [:theme]))]
    (let [size (or size 32)]
      [:img
       {:class-name (str "h-" size " w-" size)
        :src (str (if (= "light-theme" theme) "/image/black/" "/image/") icon-name ".svg")}])))

(defn facebook-share-button-comp [url]
  [:div.fb-share-button
   {:data-layout "button"
    :data-href url}])

(defn on-fb-login [logged-in?]
  (if (not logged-in?)
    (do (go (<! (timeout 2000))
            (if (not @(subscribe [:login-message-shown?]))
              (dispatch [:show-login-message "You must enable popups to allow Facebook login."])))
        (if js/FB
          (.login js/FB events/fb-login-callback (clj->js {:scope "email"}))))))

(defn fb-login-button-comp []
  [:div.flex.justify-cont-s-a
   [:button.form-button.flex.align-items-c
    {:on-click #(if @(subscribe [:fb-logged-in?])
                  (dispatch [:fb-logout])
                  (on-fb-login logged-in?))}
    [:i.fa.fa-facebook.f-s-18]
    [:span.m-l-10.f-s-14
     "Login with Facebook"]]])

(defn add-facebook-init [comp]
  (with-meta
    comp
    {:component-did-mount #(dispatch [:init-fb])}))

(def facebook-login-button
  (add-facebook-init
   fb-login-button-comp))

(def facebook-share-button
  (add-facebook-init
    facebook-share-button-comp))

(defn character-page-fb-button [id]
  [facebook-share-button
   (str
    "http://"
    js/window.location.hostname
    (routes/path-for routes/dnd-e5-char-page-route :id id))])

(def login-style
  {:color "#f0a100"})

(defn user-header-view []
  (let [username @(subscribe [:username])]
    [:div.flex.align-items-c
     [:div.user-icon [svg-icon "orc-head" 40 ""]]
     (if username
       [:span.f-w-b.t-a-r
        (if (not @(subscribe [:mobile?])) [:span.m-r-5 username])
        [:span.underline.pointer
         {:style login-style
          :on-click #(dispatch [:logout])}
         "LOG OUT"]]
       [:span.pointer.flex.flex-column.align-items-end
        [:span.orange.underline.f-w-b.m-l-5
         {:style login-style
          :on-click #(dispatch [:route-to-login])}
         [:span "LOGIN"]]])]))

(def header-tab-style
  {:width "85px"})

(def active-style {:background-color "rgba(240, 161, 0, 0.7)"})


(defn header-tab []
  (let [hovered? (r/atom false)]
    (fn [title icon on-click disabled active device-type & buttons]
      (let [mobile? (= :mobile device-type)]
        [:div.f-w-b.f-s-14.t-a-c.header-tab.m-5
         {:on-click (fn [e] (if (seq buttons)
                              #(swap! hovered? not)
                              (on-click e)))
          :on-mouse-over #(reset! hovered? true)
          :on-mouse-out #(reset! hovered? false)
          :style (merge
                  {:position :relative}
                  (if active active-style))
          :class-name (str (if disabled "disabled" "pointer")
                           " "
                           (if (not mobile?) " w-110"))}
         [:div.p-10
          {:class-name (if (not active) (if disabled "opacity-2" "opacity-6 hover-opacity-full"))}
          (let [size (if mobile? 24 48)] (svg-icon icon size ""))
          (if (not mobile?)
            [:div.title.uppercase title])]
         (if (and (seq buttons)
                  @hovered?)
           [:div.uppercase
            {:style {:position :absolute
                     :background-color "#2c3445"
                     :z-index 10000
                     :top (if mobile? 46 84)
                     :right 0}}
            (doall
             (map
              (fn [{:keys [name route]}]
                ^{:key name}
                [:div.p-10.opacity-5.hover-opacity-full
                 (let [current-route @(subscribe [:route])]
                   {:style (if (or (= route current-route)
                                   (= route (get current-route :handler))) active-style)
                    :on-click (fn [e]
                                (dispatch [:route route])
                                (.stopPropagation e))})
                 name])
              buttons))])]))))


(def social-icon-style
  {:color :white
   :font-size "20px"})

(defn social-icon [icon link]
  [:a.p-5.opacity-5.hover-opacity-full.main-text-color
   {:style social-icon-style
    :href link :target :_blank}
   [:i.fa
    {:class-name (str "fa-" icon)}]])

(def search-input-style
  {:height "60px"
   :margin-top "0px"
   :border :none
   :font-size "28px"
   :background-color :transparent
   :color :white})

(def search-icon-style
  {:top 6
   :right 25})

(defn app-header []
  (let [device-type @(subscribe [:device-type])
        mobile? (= :mobile device-type)
        active-route @(subscribe [:route])]
    [:div#app-header.app-header.flex.flex-column.justify-cont-s-b.white
     [:div.app-header-bar.container
      [:div.content
       [:div.flex.align-items-c.h-100-p
        [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20.h-100-p
         [:img.orcpub-logo.h-32.w-120.pointer
          {:src "/image/orcpub-logo.svg"
           :on-click #(dispatch [:route routes/default-route])}]
         (let [search-text @(subscribe [:search-text])
               search-text? @(subscribe [:search-text?])]
           [:div
            {:class-name (if mobile? "p-l-10 p-r-10" "p-l-20 p-r-20 flex-grow-1")}
            [:div.b-rad-5.flex.align-items-c
             {:style {:background-color "rgba(0,0,0,0.15)"}}
             (if (not mobile?)
               [:div.p-l-20.flex-grow-1
                [:input.w-100-p.main-text-color
                 {:style (if search-text?
                           (merge
                            search-input-style
                            {:color :transparent})
                           search-input-style)
                  :value search-text
                  :on-key-press #(if (= "Enter" (.-key %)) (dispatch [:set-search-text search-text]))
                  :on-change #(dispatch [:set-search-text (event-value %)])}]])
             [:div.opacity-1.p-r-10.pointer
              {:class-name (if mobile? "opacity-5" "opacity-8")
               :on-click #(dispatch [:open-orcacle])}
              (svg-icon "magnifying-glass" (if mobile? 32 48) "")]]])
         [user-header-view]]]]]
     [:div.container
      [:div.content
       [:div.flex.w-100-p.align-items-end
        {:class-name (if mobile? "justify-cont-s-b" "justify-cont-s-b")}
        [:div
         [:a {:href "https://www.patreon.com/orcpub" :target :_blank}
          [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
           {:src (if mobile?
                   "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
                   "https://c5.patreon.com/external/logo/become_a_patron_button.png")}]]
         (if (not mobile?)
           [:div.main-text-color.p-10
            (social-icon "facebook" "https://www.facebook.com/orcpub")
            (social-icon "twitter" "https://twitter.com/OrcPub")
            (social-icon "reddit-alien" "https://www.reddit.com/r/orcpub/")])]
        [:div.flex.m-b-5.m-r-5
         [header-tab
          "characters"
          "battle-gear"
          #(dispatch [:route routes/dnd-e5-char-list-page-route])
          false
          (routes/dnd-e5-char-page-routes (or (:handler active-route) active-route))
          device-type
          {:name "Character List"
           :route routes/dnd-e5-char-list-page-route}
          {:name "Builder"
           :route routes/dnd-e5-char-builder-route}
          {:name "Parties"
           :route routes/dnd-e5-char-parties-page-route}]
         [header-tab
          "spells"
          "spell-book"
          #(dispatch [:route routes/dnd-e5-spell-list-page-route])
          false
          (routes/dnd-e5-spell-page-routes (or (:handler active-route) active-route))
          device-type]
         [header-tab
          "monsters"
          "hydra"
          #(dispatch [:route routes/dnd-e5-monster-list-page-route])
          false
          (routes/dnd-e5-monster-page-routes (or (:handler active-route) active-route))
          device-type]
         [header-tab
          "items"
          "all-for-one"
          #(dispatch [:route routes/dnd-e5-item-list-page-route])
          false
          (routes/dnd-e5-item-page-routes
           (or (:handler active-route)
               active-route))
          device-type
          {:name "Item List"
           :route routes/dnd-e5-item-list-page-route}
          {:name "Item Builder"
           :route routes/dnd-e5-item-builder-page-route}]]]]]]))

(defn legal-footer []
  [:div.m-l-15.m-b-10.m-t-10 {:style {:text-align :left}}
   [:span "© 2017 OrcPub"]
   [:a.m-l-5 {:href "/terms-of-use" :target :_blank} "Terms of Use"]
   [:a.m-l-5 {:href "/privacy-policy" :target :_blank} "Privacy Policy"]])

(defn registration-page [content]
  [:div.sans.h-100-p.flex
   {:style {:flex-direction :column}}
   [:div.flex.justify-cont-s-a.align-items-c.flex-grow-1.h-100-p
    [:div.registration-content
     {:style {:background-color :white
              :border "1px solid white"
              :color text-color}}
     [:div.flex.h-100-p
      [:div.flex {:style {:flex-direction :column
                          :width "435px"}}
       [:div.flex.justify-cont-s-a.align-items-c
        {:style {:height "65px"
                 :background-color "#1a2532"
                 :border-right "1px solid white"}}
        [:img.pointer
         {:src "/image/orcpub-logo.svg"
          :style {:height "25.3px"}
          :on-click #(dispatch [:route :default])}]]
       [:div.flex-grow-1 content]
       [legal-footer]]
      [:div.registration-image
       {:style {:background-image "url(/image/shutterstock_432001912.jpg)"
                :background-size "1200px 800px"
                :background-position "-350px 0px"
                :background-clip :content-box
                :width "350px"
                :min-height "600px"}}]]]]])

(defn verify-failed []
  (let [params (r/atom {})]
    (fn []
      (registration-page
       [:div.flex.justify-cont-s-b {:style {:text-align :center
                           :flex-direction :column}}
        [:div.p-20
         [:div.f-w-b.f-s-24.p-b-10
          "Your key has expired."]
         [:div "You must verify your email within 24 hours of registering. Send another verification email by submitting you address here:"]
         [base-input
          {:name :email
           :value (:email @params)
           :type :email
           :placeholder "Email"
           :style default-input-style
           :on-change (partial set-value params :email)}]
         [:button.form-button.m-l-20.m-t-10
          {:style {:height "40px"
                   :width "174px"
                   :font-size "16px"
                   :font-weight "600"}
           :on-click #(dispatch [:re-verify @params])}
          "RESEND"]]]))))

(def message-style
  {:padding "10px"
   :border-radius "5px"
   :display :flex
   :justify-content :space-between})

(defn message [message-type message-text close-event]
  [:div.pointer.f-w-b ;;.h-0.opacity-0.fade-out
   {:on-click #(dispatch close-event)}
   [:div.main-text-color
    {:style message-style
     :class-name (case message-type
                   :error "bg-red"
                   "bg-green")}
    [:span message-text]
    [:i.fa.fa-times]]])

(defn send-password-reset-page []
  (let [params (r/atom {})]
    (fn [error-message]
      (let [email (:email @params)
            bad-email? (registration/bad-email? email)]
        (registration-page
         [:div.flex.justify-cont-s-b.w-100-p
          {:style {:text-align :center
                   :flex-direction :column}}
          [:div.p-t-10
           (if error-message [:div.red.m-b-20 error-message])
           [:div.f-w-b.f-s-24.p-b-10
            "Send Password Reset Email"]
           [:div.m-b-10 "Submit your email address here and we will send you a link to reset your password."]
           [form-input
            {:title "Email"
             :key :email
             :messages (if bad-email?
                           ["Not a valid email address"]
                           [])
             :type :email
             :value email
             :on-change (partial set-value params :email)}]
           (if @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5
              [message
               :error
               @(subscribe [:login-message])
               [:hide-login-message]]])
           [:button.form-button.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class-name (if bad-email? "disabled opacity-5 hover-no-shadow")
             :on-click #(if (not bad-email?) (dispatch [:send-password-reset @params]))}
            "SUBMIT"]]])))))

(defn password-reset-expired-page []
  [send-password-reset-page "Your reset link has expired, you must complete the reset within 24 hours. Please use the form below to send another reset email."])

(defn password-reset-used-page []
  [send-password-reset-page "Your reset link has already been used. Please use the form below to send another reset email."])

(defn password-validation-messages [password]
  (-> password
      registration/validate-password
      :password))

(defn password-reset-page []
  (let [params (r/atom {})]
    (fn []
      (let [password (:password @params)
            verify-password (:verify-password @params)
            password-messages (password-validation-messages password)
            different? (not= password verify-password)
            invalid? (or (seq password-messages)
                         different?)]
        (registration-page
         [:div.flex.justify-cont-s-b {:style {:text-align :center
                                              :flex-direction :column}}
          [:div.p-20
           [:div.f-w-b.f-s-24.p-b-10
            "Reset Password"]
           [:div "Create a new password."]
           [form-input {:title "Password"
                        :key :password
                        :value password
                        :type :password
                        ;;:messages password-messages
                        :on-change (fn [e] (swap! params assoc :password (event-value e)))}]
           [form-input {:title "Verify Password"
                        :key :verify-password
                        :value verify-password
                        :type :password
                        :messages (if different? ["Passwords do not match"])
                        :on-change (fn [e] (swap! params assoc :verify-password (event-value e)))}]
           (if @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5 [message
                                      :error
                                      @(subscribe [:login-message])
                                      [:hide-login-message]]])
           [:button.form-button.m-l-20.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class-name (if invalid? "opacity-5 hover-no-shadow cursor-disabled")
             :on-click #(if (not invalid?) (dispatch [:password-reset @params]))}
            "SUBMIT"]]])))))

(defn login-link []
  [:span.underline.f-w-b.m-l-10.pointer.orange
   {:on-click #(dispatch [:route-to-login])}
   "LOGIN"])

(defn verify-success []
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Success! Registration is complete"]
    [:div.m-t-20 "You can now"]
    [login-link]]))

(defn password-reset-success []
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Your password has been successfully reset"]
    [:div.m-t-20 "You can now log in"]
    [login-link]]))

(defn email-sent [text]
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Check your email"]
    [:div.p-20
     text]]))

(defn verify-sent []
  (email-sent
   [:div
    [:span "We sent a verification email to "]
    [:span.f-w-b.red.f-s-18 @(subscribe [:temp-email])]
    [:span ". You must verify to complete registration and the link we sent will only be valid for 24 hours."]]))

(defn password-reset-sent []
  (email-sent
   (str "We sent an email to "
        @(subscribe [:temp-email])
        " with a link to reset your password.")))

(defn register-form []
  (let [registration-validation @(subscribe [:registration-validation])
        registration-form @(subscribe [:registration-form])
        send-updates? (not= false (:send-updates? registration-form))
        password-strength (registration/password-strength (:password registration-form))]
    (registration-page
     [:div {:style {:text-align :center}}
      [:div {:style {:color orange
                     :font-weight :bold
                     :font-size "36px"
                     :text-transform :uppercase
                     :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                     :margin-top "20px"}}
       "join for free"]
      [:div.f-s-16.m-t-20 "Join now to save your characters and more!"]
      [:div.m-t-10
       [form-input {:title "First and Last Name"
                    :key :first-and-last-name
                    :value (:first-and-last-name registration-form)
                    :messages (:first-and-last-name registration-validation)
                    :type :text
                    :on-change (fn [e] (dispatch [:registration-first-and-last-name (event-value e)]))}]
       [form-input {:title "Email"
                    :key :email
                    :value (:email registration-form)
                    :messages (:email registration-validation)
                    :type :email
                    :on-change (fn [e] (dispatch [:registration-email (event-value e)]))}]
       [form-input {:title "Verify Email"
                    :key :verify-email
                    :value (:verify-email registration-form)
                    :messages (:verify-email registration-validation)
                    :type :email
                    :on-change (fn [e] (dispatch [:registration-verify-email (event-value e)]))}]
       [form-input {:title "Username"
                    :key :username
                    :value (:username registration-form)
                    :messages (:username registration-validation)
                    :type :username
                    :on-change (fn [e] (dispatch [:registration-username (event-value e)]))}]
       [form-input {:title "Password"
                    :key :password
                    :value (:password registration-form)
                    :messages (:password registration-validation)
                    :type :password
                    :on-change (fn [e] (dispatch [:registration-password (event-value e)]))}]
       (let [[color text]
              (cond
                (= 5 password-strength) ["bg-green" "Strong"]
                (< 1 password-strength 5) ["bg-orange" "Moderate"]
                :else ["bg-red" "Weak"])]
         [:div.p-r-10.p-l-10.p-t-5
          [:div
           {:style {:position :relative
                    :height "30px"}}
           [:div.b-rad-5
            {:style {:top 0
                     :left 0
                     :height "30px"
                     :opacity "0.7"
                     :width "100%"
                     :position :absolute}
             :class-name color}]
           [:div.b-rad-5.password-strength-meter
            {:style {:top 0
                     :left 0
                     :position :absolute
                     :height "30px"
                     :transition "width 1s"
                     :width (str (* 100 (float (/ password-strength 5))) "%")}
             :class-name color}]
           [:div.main-text-color.p-l-10.b-rad-5
            {:style {:position :absolute
                     :padding-top "6px"}}
             [:span "Password Strength:"]
             [:span.f-w-b.m-l-5 text]]]])
       [:div.m-t-20
        {:style {:text-align :left
                 :margin-left "15px"}}
        [:i.fa.fa-check.f-s-14.pointer
         {:class-name (if send-updates? "orange" "white")
          :style {:margin-top "-3px"
                  :border-color "#f0a100"
                  :border-style :solid
                  :border-width "1px"
                  :border-bottom-width "3px"}
          :on-click #(dispatch [:registration-send-updates? (not send-updates?)])}]
        [:span.m-l-5 "Yes! Send me updates about OrcPub."]]
       [:div.m-t-30
        [:div.p-10
         [:span "Already have an account?"]
         (login-link)]
        [:button.form-button
         {:style {:height "40px"
                  :width "174px"
                  :font-size "16px"
                  :font-weight "600"}
          :class-name (if (seq registration-validation) "opacity-5 hover-no-shadow cursor-disabled")
          :on-click #(if (empty? registration-validation)
                       (dispatch [:register]))}
         "JOIN"]]]
      [:div.m-t-5.p-r-10.p-l-10
       [:span.f-s-14
        "By clicking JOIN you agree to our"
        [:a.m-l-5 {:href "/terms-of-use" :target :_blank
                   :style {:color text-color}} "Terms of Use"]
        [:span.m-l-5 "and that you've read our"]
        [:a.m-l-5 {:href "/privacy-policy" :target :_blank
                   :style {:color text-color}} "Privacy Policy"]]]])))


(defn login-page []
  (let [params (r/atom {})]
    (fn []
      (registration-page
       [:div {:style {:text-align :center}}
        [:div {:style {:color orange
                       :font-weight :bold
                       :font-size "36px"
                       :text-transform :uppercase
                       :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                       :margin-top "20px"}}
         "LOGIN"]
        [:div.m-t-10
         [facebook-login-button]]
        [:div
         {:style {:margin-top "50px"}}
         [form-input {:title "Username or Email"
                      :key :username
                      :value (:username @params)
                      :type :username
                      :on-change #(swap! params assoc :username (event-value %))}]
         [form-input {:title "Password"
                      :key :password
                      :value (:password @params)
                      :type :password
                      :on-change #(swap! params assoc :password (event-value %))}]
         (if @(subscribe [:login-message-shown?])
           [:div.m-t-5.p-r-5.p-l-5 [message
             :error
             @(subscribe [:login-message])
             [:hide-login-message]]])
         [:div.m-t-10
          [:button.form-button
           {:style {:height "40px"
                    :width "174px"
                    :font-size "16px"
                    :font-weight "600"}
            :on-click #(dispatch [:login @params true])}
           "LOGIN"]
          [:div.m-t-20
           [:span "Don't have a login? "]
           [:span.orange.underline.pointer
            {:on-click #(dispatch [:route routes/register-page-route {:secure true :no-return? true}])}
            "REGISTER NOW"]]
          [:div.m-t-20
           [:span "Forgot your password? "]
           [:span.orange.underline.pointer
            {:on-click #(dispatch [:route routes/send-password-reset-page-route {:secure? true :no-return? true}])}
            "RESET PASSWORD"]]]]]))))

(def loading-style
  {:position :absolute
   :height "100%"
   :width "100%"
   :top 0
   :bottom 0
   :right 0
   :left 0
   :z-index 100
   :background-color "rgba(0,0,0,0.6)"})

(defn header [title button-cfgs]
  (let [device-type @(subscribe [:device-type])]
    [:div.w-100-p
     [:div.flex.align-items-c.justify-cont-s-b.flex-wrap
      [:h1.f-s-36.f-w-b.m-t-5.m-l-10
       {:class-name (if (not= :mobile device-type) "m-t-21 m-b-20")}
       title]
      [:div.flex.align-items-c.justify-cont-end.flex-wrap.m-r-10.m-l-10
       (map-indexed
        (fn [i {:keys [title icon on-click style class-name] :as cfg}]
          (if (vector? cfg)
            (with-meta
              cfg
              {:key i})
            ^{:key i}
            [:button.form-button.h-40.m-l-5.m-t-5.m-b-5
             {:on-click on-click
              :class-name class-name
              :style style}
             [:span
              [:i.fa.f-s-18
               (if icon {:class-name (str "fa-" icon)})]]
             [:span.m-l-5.header-button-text title]]))
        button-cfgs)]]
     (if @(subscribe [:confirmation-shown?])
       [:div.flex.justify-cont-end.m-r-10.m-b-20.m-l-10
        (let [cfg @(subscribe [:confirmation-cfg])]
          [:div
           [:div.f-w-b (:question cfg)]
           [:div.flex.justify-cont-end.m-t-5
            [:button.form-button
             {:on-click #(dispatch [:hide-confirmation])}
             "CANCEL"]
            [:button.link-button.underline.f-w-b
             {:on-click #(do
                           (if (:pre cfg)
                             ((:pre cfg)))
                           (dispatch [:confirm (:event cfg)]))}
             (:confirm-button-text cfg)]]])])
     (if @(subscribe [::char/options-shown?])
       [:div.bg-light.m-b-10 @(subscribe [::char/options-component])])
     (if @(subscribe [:message-shown?])
       [:div.p-b-10.p-r-10.p-l-10
        [message
         @(subscribe [:message-type])
         @(subscribe [:message])
         [:hide-message]]])]))

(def debug-data-style {:width "400px" :height "400px"})

(defn debug-data []
  (let [expanded? (r/atom false)]
    (fn []
      [:div.t-a-r
       [:div.orange.pointer.underline
        {:on-click #(swap! expanded? not)}
        [:i.fa.fa-bug {:class-name (if @expanded? "white")}]]
       (if @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (str {:browser (user-agent/browser)
                        :browser-version (user-agent/browser-version)
                        :device-type (user-agent/device-type)
                        :platform (user-agent/platform)
                        :platform-version (user-agent/platform-version)
                        :character (char/to-strict @(subscribe [:character]))})}])])))

(defn dice-roll-result [{:keys [total rolls mod raw-mod plus-minus]}]
  [:div.white.f-s-32.flex.align-items-c
   (svg-icon "rolling-dices" 36 "")
   [:div.m-l-10
    [:span.f-w-b total]
    [:span.m-l-10.m-r-10 "="]
    [:span (s/join " + " rolls)]
    (if (not= 0 raw-mod) [:span (if (pos? plus-minus) " + " " - ")])
    (if (not= 0 raw-mod) [:span raw-mod])]])

(defn spell-field [name value]
  [:div
   [:span.f-w-b name ":"]
   [:span.m-l-10 value]])

(defn columns-style [num]
  {:line-height "19px"
   :column-count num
   :-webkit-column-count num
   :-moz-column-count num})

(def two-columns-style
  (columns-style 2))

(def three-columns-style
  (columns-style 3))

(def two-columns-second-empty-style
  {:width "50%"})

(defn paragraphs [str & [single-column?]]
  (let [mobile? @(subscribe [:mobile?])
        ps (s/split str #"\n")
        p-els (doall
               (map-indexed
                (fn [i p]
                  ^{:key i} [:p p])
                ps))]
    (if (or mobile?
            single-column?)
      [:div
       p-els]
      [:div
       {:style (if (= 1 (count ps))
                 two-columns-second-empty-style
                 two-columns-style)}
       p-els])))

(defn requires-attunement [attunement]
  (str
   " (requires attunement"
   (if (-> attunement set :any not)
     (str " by a "
          (common/list-print
           (map
            (fn [kw]
              (case kw
                :good " creature of good alignment"
                :evil " creature of evil alignment"
                (common/kw-to-name kw)))
            attunement) "or")))
   ")"))

(defn item-summary [{:keys [::mi/owner ::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement] :as item}]
  (if item
    [:div.p-b-20.flex.align-items-c
     (if owner
       [:div.m-r-5 [svg-icon "beer-stein" 24]])
     [:div
      [:span.f-s-24.f-w-b (or (:name item) name)]
      [:div.f-s-16.i.f-w-b.opacity-5
       (str (if type (s/capitalize (common/kw-to-name type)))
            (if (keyword? item-subtype)
              (str " (" (s/capitalize (common/kw-to-name item-subtype)) ")"))
            ", "
            (if (string? rarity)
              rarity
              (common/kw-to-name rarity))
            (if attunement
              (requires-attunement attunement)))]]]))

(defn item-details [{:keys [::mi/summary ::mi/description ::mi/attunment]} single-column?]
  (if (or summary description)
    (paragraphs (or summary description) single-column?)))

(defn item-component [item & [hide-summary? single-column?]]
  [:div.m-l-10.l-h-19
   (if (not hide-summary?)
     [:div [item-summary item]])
   [:div [item-details item single-column?]]])

(defn magic-item-result [item]
  [:div.white
   [:div.flex
    (svg-icon "orb-wand" 36 "")
    [item-component item]]])

(defn name-result [{:keys [sex race subrace] :as result}]
  [:div.white
   [:span.f-s-24.f-w-b (:name result)]
   [:div
    [:span.f-s-14.opacity-5.i (s/join " " (map (fn [k] (if k (name k))) [sex race subrace]))]]])

(defn tavern-name-result [name]
  [:span.f-s-24.f-w-b.white name])

(defn spell-summary [name level school include-name? & [subheader-size]]
  [:div.p-b-20
   (if include-name? [:span.f-s-24.f-w-b name])
   [:div.i.f-w-b.opacity-5
    {:class-name (str "f-s-" (or subheader-size 18))}
    (str (if (pos? level)
           (str (common/ordinal level) "-level"))
         " "
         (s/capitalize school)
         (if (zero? level)
           " cantrip"))]])

(defn spell-component [{:keys [name level school casting-time range duration components description summary page source] :as spell} include-name? & [subheader-size]]
  [:div.m-l-10.l-h-19
   [spell-summary name level school include-name? subheader-size]
   (spell-field "Casting Time" casting-time)
   (spell-field "Range" range)
   (spell-field "Duration" duration)
   (let [{:keys [verbal somatic material material-component]} components]
     (spell-field "Components" (str (s/join ", " (remove
                                                  nil?
                                                  [(if verbal "V")
                                                   (if somatic "S")
                                                   (if material "M")]))
                                    (if material-component
                                      (str " (" material-component ")")))))
   [:div.m-t-10
    (if description
      (paragraphs description)
      [:div
       (if summary (paragraphs summary))
       [:span (str "(" (disp/source-description source page) " for more details)")]])]])

(defn spell-result [spell]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 "")
    [spell-component spell true]]])

(defn spell-results [results]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 36)
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name level school casting-time range duration components description summary page source]}]
         ^{:key name}
         [:div.pointer
          {:on-click (fn [_]
                       (let [spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
                             spell-page-route (routes/match-route spell-page-path)]
                         (dispatch [:route spell-page-route])))}
          [spell-summary name level school true 14]])
       results))]]])

(defn monster-subheader [size type subtypes alignment]
  (str (s/capitalize (common/kw-to-name size))
       " "
       (common/kw-to-name type)
       (if (seq subtypes)
         (str " (" (s/join ", " (map common/kw-to-name subtypes)) ")"))
       ", "
       alignment))

(defn monster-summary [name size type subtypes alignment]
  [:div.m-r-10
   [:div name]
   [:div.f-s-14.i.opacity-5 (monster-subheader size type subtypes alignment)]])

(defn monster-results [results]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name size type subtypes alignment]}]
         ^{:key name}
         [:div.pointer.f-s-24.f-w-600.m-b-20
          {:on-click (fn [_]
                       (let [monster-page-path (routes/path-for routes/dnd-e5-monster-page-route :key key)
                             monster-page-route (routes/match-route monster-page-path)]
                         (dispatch [:route monster-page-route])))}
          [monster-summary name size type subtypes alignment]])
       results))]]])


(defn print-bonus-map [m]
  (s/join ", "
          (map
           (fn [[k v]] (str (s/capitalize (clojure.core/name k)) " " (common/bonus-str v)))
           m)))

(defn monster-component [{:keys [name size type subtypes hit-points alignment armor-class armor-notes speed saving-throws skills damage-vulnerabilities damage-resistances damage-immunities condition-immunities senses languages challenge traits actions legendary-actions source page] :as monster}]
  [:div.m-l-10.l-h-19
   (if (not @(subscribe [:mobile?])) {:style two-columns-style})
   [:span.f-s-24.f-w-b name]
   [:div.f-s-18.i.f-w-b (monster-subheader size type subtypes alignment)]
   (spell-field "Armor Class" (str armor-class (if armor-notes (str " (" armor-notes ")"))))
   (let [{:keys [mean die-count die modifier]} hit-points]
     (spell-field "Hit Points" (str die-count
                                    "d"
                                    die
                                    (if modifier (common/mod-str modifier))
                                    (if mean (str " (" mean ")")))))
   (spell-field "Speed" speed)
   [:div.m-t-10.flex.justify-cont-s-a.m-b-10
    {:style {:max-width "300px"}}
    (doall
     (map
      (fn [ability-key]
        ^{:key ability-key}
        [:div.t-a-c.p-5
         [:div.f-w-b.f-s-14 (s/upper-case (clojure.core/name ability-key))]
         (let [ability-value (get monster ability-key)]
           [:div ability-value " (" (common/bonus-str (opt/ability-bonus ability-value)) ")"])])
      [:str :dex :con :int :wis :cha]))]
   (if (seq saving-throws)
     (spell-field "Saving Throws" (print-bonus-map saving-throws)))
   (if skills (spell-field "Skills" (print-bonus-map skills)))
   (if damage-vulnerabilities (spell-field "Damage Vulnerabilities" damage-vulnerabilities))
   (if damage-resistances (spell-field "Damage Resistances" damage-resistances))
   (if damage-immunities (spell-field "Damage Immunities" damage-immunities))
   (if condition-immunities (spell-field "Condition Immunities" condition-immunities))
   (if senses (spell-field "Senses" senses))
   (if languages (spell-field "Languages" languages))
   (if challenge (spell-field "Challenge" (str
                                           (case challenge
                                             0.125 "1/8"
                                             0.25 "1/4"
                                             0.5 "1/2"
                                             challenge)
                                           " ("
                                           (monsters/challenge-ratings challenge)
                                           " XP)")))
   (if traits
     [:div.m-t-20
      (doall
       (map-indexed
        (fn [i {:keys [name description]}]
          ^{:key i}
          [:div.m-t-10 (spell-field name description)])
        traits))])
   (if actions
     [:div.m-t-20
      [:div.i.f-w-b.f-s-18 "Actions"]
      [:div
       (doall
        (map-indexed
         (fn [i {:keys [name description]}]
           ^{:key i}
           [:div.m-t-10 (spell-field name description)])
         actions))]])
   (if legendary-actions
     [:div.m-t-20
      [:div.i.f-w-b.f-s-18 "Legendary Actions"]
      (if (:description legendary-actions)
        [:div (:description legendary-actions)])
      (if (:actions legendary-actions)
        [:div
         (doall
          (map-indexed
           (fn [i {:keys [name description]}]
             ^{:key i}
             [:div.m-t-10 (spell-field name description)])
           (:actions legendary-actions)))])])])

(defn monster-result [monster]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [monster-component monster]]])

(defn search-results []
  (if-let [{{:keys [result] :as top-result} :top-result
            results :results
            :as search-results}
           @(subscribe [:search-results])]
    [:div
     (if top-result
       [:div.p-20.m-b-20
        (let [type (:type top-result)]
          (case type
            :dice-roll (dice-roll-result result)
            :spell (spell-result result)
            :monster (monster-result result)
            :magic-item (magic-item-result result)
            :name (name-result result)
            :tavern-name (tavern-name-result result)
            nil))])
     (if (seq results)
       (doall
        (map
         (fn [{:keys [type results]}]
           ^{:key type}
           [:div.p-20
            (case type
              :spell (spell-results results)
              :monster (monster-results results))])
         results)))]))

(def oracle-frame-style
  {:overflow-y :scroll
   :position :fixed
   :z-index 1
   :background-color "rgba(0,0,0,0.95)"
   :top 0
   :left 0
   :right 0
   :bottom 0})

(def close-icon-style
  {:top 0
   :right 0
   :padding "17px"})


(defn content-page [title button-cfgs content]
  (let [orcacle-open? @(subscribe [:orcacle-open?])
        theme @(subscribe [:theme])]
    [:div.app
     {:class-name theme
      :on-scroll (fn [e]
                   (if (not orcacle-open?)
                     (let [app-header (js/document.getElementById "app-header")
                           header-height (.-offsetHeight app-header)
                           scroll-top (.-scrollTop (.-target e))
                           sticky-header (js/document.getElementById "sticky-header")
                           app-main (js/document.getElementById "app-main")
                           scrollbar-width (- js/window.innerWidth (.-offsetWidth app-main))
                           header-container (js/document.getElementById "header-container")]
                       (set! (.-paddingRight (.-style header-container)) (str scrollbar-width "px"))
                       (if (>= scroll-top header-height)
                         (set! (.-display (.-style sticky-header)) "block")
                         (set! (.-display (.-style sticky-header)) "none")))))}
     [download-form]
     (if @(subscribe [:loading])
       [:div {:style loading-style}
        [:div.flex.justify-cont-s-a.align-items-c.h-100-p
         [:img.h-200.w-200.m-t-200 {:src "/image/spiral.gif"}]]])
     [app-header]
     (let [search-text @(subscribe [:search-text])]
       (if orcacle-open?
         [:div.flex.flex-column.h-100-p.white
          {:style oracle-frame-style}
          [:i.fa.fa-times-circle.f-s-24.orange.pointer
           {:on-click #(dispatch [:close-orcacle])
            :style {:position :fixed
                    :top 20
                    :right 40}}]
          [:div
           [:div.flex.justify-cont-s-a.m-t-10
            [:div.flex.align-items-c.pointer
             {:on-click #(dispatch [:close-orcacle])}
             [:span.f-s-32 "Orcacle"]
             [:div.m-l-10 (svg-icon "hood" 48 "")]]]]
          [:div.p-10
           [:div.posn-rel
            [:input.input
             {:value search-text
              :on-change #(dispatch [:set-search-text (event-value %)])
              :on-key-press #(if (= "Enter" (.-key %)) (dispatch [:set-search-text search-text]))
              :style (merge search-input-style
                            {:background-color "rgba(255,255,255,0.1)"})}]
            [:i.fa.fa-times.posn-abs.f-s-24.pointer
             {:style close-icon-style
              :on-click #(dispatch [:set-search-text ""])}]]
           [:span.f-s-14.i.opacity-5 "\"8d10 + 2\", \"magic missile\", \"kobold\", \"female calishite name\", \"tavern name\", etc."]]
          [:div.flex-grow-1
           [search-results]]]))
     (let [hdr [header title button-cfgs]]
       [:div
        [:div#sticky-header.sticky-header.w-100-p.posn-fixed
         [:div.flex.justify-cont-c
          [:div#header-container.f-s-14.main-text-color.content
           hdr]]]
        [:div.flex.justify-cont-c.main-text-color
         [:div.content hdr]]
        [:div#app-main.container
         [:div.content.w-100-p content]]
        [:div.main-text-color.flex.justify-cont-c
         [:div.content.f-w-n.f-s-12
          [:div.flex.justify-cont-s-b.align-items-c.w-100-p.flex-wrap
           [:div.p-10
            [:div.m-b-5 "Icons made by Lorc, Caduceus, and Delapouite. Available on " [:a.orange {:href "http://game-icons.net"} "http://game-icons.net"]]]
           [:div.m-l-10
            [:a.orange {:href "https://muut.com/orcpub" :target :_blank} "Feedback/Bug Reports"]]
           [:div.m-l-10.m-r-10
            [:span.m-r-5 "© 2017 OrcPub"]
            [:a.orange {:href "/privacy-policy" :target :_blank} "Privacy Policy"]
            [:a.orange.m-l-5 {:href "/terms-of-use" :target :_blank} "Terms of Use"]]]
          [debug-data]]]])]))

(def row-style
  {:border-bottom "1px solid rgba(255,255,255,0.5)"})

(def light-row-style
  {:border-bottom "1px solid rgba(0,0,0,0.5)"})

(def list-style
  {:border-top "2px solid rgba(255,255,255,0.5)"})

(def thumbnail-style
  {:height "100px"
   :max-width "200px"})

(defn other-user-component [owner & [text-classes show-follow?]]
  (let [following-users @(subscribe [:following-users])
        following? (get following-users owner)
        username @(subscribe [:username])]
    [:div.flex.m-l-10.align-items-c
     (svg-icon "orc-head" 32)
     [:div.f-s-18.m-l-5
      {:class-name text-classes}
      owner]
     (if (and show-follow? username (not= username owner))
       [:button.form-button.m-l-10.p-6
        {:on-click #(dispatch [(if following?
                                 :unfollow-user
                                 :follow-user)
                               owner])}
        (if following?
          "unfollow"
          "follow")])]))

(defn character-summary-2 [{:keys [::char/character-name
                                   ::char/image-url
                                   ::char/race-name
                                   ::char/subrace-name
                                   ::char/classes]}
                           include-name?
                           owner
                           show-owner?
                           show-follow?]
  (let [username @(subscribe [:username])]
    [:div.flex.justify-cont-s-b.w-100-p.align-items-c
     [:div.flex.align-items-c
      (if image-url
        [:img.m-r-20.m-t-10.m-b-10 {:src image-url
                                    :style thumbnail-style}])
      [:div.flex.character-summary.m-t-20.m-b-20
       (if (and character-name include-name?) [:span.m-r-20.m-b-5 character-name])
       [:span.m-r-10.m-b-5
        [:span race-name]
        [:div.f-s-12.m-t-5.opacity-6 subrace-name]]
       (if (seq classes)
         [:span.flex
          (map-indexed
           (fn [i v]
             (with-meta v {:key i}))
           (interpose
            [:span.m-l-5.m-r-5 "/"]
            (map
             (fn [{:keys [::char/class-name ::char/level ::char/subclass-name]}]
               (let []
                 [:span
                  [:span (str class-name " (" level ")")]
                  [:div.f-s-12.m-t-5.opacity-6 (if subclass-name subclass-name)]]))
             classes)))])]]
     (if (and show-owner?
              (some? owner)
              (some? username)
              (not= username owner))
       [:div.m-l-10 [other-user-component owner nil show-follow?]])]))

(defn character-summary [id & [include-name?]]
  (let [character-name @(subscribe [::char/character-name id])
        image-url @(subscribe [::char/image-url id])
        race @(subscribe [::char/race id])
        subrace @(subscribe [::char/subrace id])
        levels @(subscribe [::char/levels id])
        classes @(subscribe [::char/classes id])
        {:keys [::se/owner] :as strict-character} @(subscribe [::char/character id])]
    (character-summary-2
     {::char/character-name character-name
      ::char/image-url image-url
      ::char/race-name race
      ::char/subrace-name subrace
      ::char/classes (map
                      (fn [class-kw]
                        (let [{:keys [class-name class-level subclass-name] :as cfg}
                              (get levels class-kw)]
                          {::char/class-name class-name
                           ::char/level class-level
                           ::char/subclass-name subclass-name}))
                      classes)}
     include-name?
     owner
     true
     true)))

(defn realize-char [built-char]
  (reduce-kv
   (fn [m k v]
     (let [realized-value (es/entity-val built-char k)]
       (if (fn? realized-value)
         m
         (assoc m k realized-value))))
   (sorted-map)
   built-char))

(def summary-style
  {:padding "33px 0"})

(defn display-section [title icon-name value & [list? buttons]]
  [:div.m-t-20
   [:div.flex.justify-cont-s-b
    [:div.flex.align-items-c
     (if icon-name (svg-icon icon-name))
     [:span.m-l-5.f-s-16.f-w-600 title]]
    (if (seq buttons)
      (apply
       conj
       [:div]
       buttons))]
   [:div {:class-name (if list? "m-t-0" "m-t-4")}
    [:span.f-s-24.f-w-600
     value]]])

(defn list-display-section [title image-name values]
  (if (seq values)
    (display-section
     title
     image-name
     [:span.m-t-5.f-s-14.f-w-n.i
      (s/join
       ", "
       values)]
     true)))

(defn svg-icon-section [title icon-name content]
  [:div.m-t-20
   [:span.f-s-16.f-w-600 title]
   [:div.flex.align-items-c
    (svg-icon icon-name)
    [:div.f-s-24.m-l-10.f-w-b content]]])

(defn armor-class-section [armor-class armor-class-with-armor equipped-armor]
  (let [equipped-armor-full (mi/equipped-armor-details equipped-armor)
        shields (filter #(= :shield (:type %)) equipped-armor-full)
        armor (filter #(not= :shield (:type %)) equipped-armor-full)
        display-rows (for [a (conj armor nil)
                           shield (conj shields nil)]
                       (let [el (if (= nil a shield) :span :div)]
                         ^{:key (common/name-to-kw (str (:name a) (:name shield)))}
                        [el
                         [el
                          [:span.f-s-24.f-w-b (armor-class-with-armor a shield)]
                          [:span.display-section-qualifier-text (str "("
                                                                     (if a (:name a) "unarmored")
                                                                     (if shield (str " & " (:name shield)))
                                                                     ")")]]]))]
    (svg-icon-section
     "Armor Class"
     "checked-shield"
     [:span
       (first display-rows)
       [:div
        (doall (rest display-rows))]])))

(defn speed-section [built-char all-armor]
  (let [speed (char/base-land-speed built-char)
        speed-with-armor (char/land-speed-with-armor built-char)
        unarmored-speed-bonus (char/unarmored-speed-bonus built-char)
        equipped-armor (char/normal-armor-inventory built-char)]
    [svg-icon-section
     "Speed"
     "walking-boot"
     [:span
      [:span
       [:span (+ (or unarmored-speed-bonus 0)
                 (if speed-with-armor
                   (speed-with-armor nil)
                   speed))]
       (if (or unarmored-speed-bonus
               speed-with-armor)
         [:span.display-section-qualifier-text "(unarmored)"])]
      (if speed-with-armor
        [:div
         (doall
          (map
           (fn [[armor-kw _]]
             (let [armor (mi/all-armor-map armor-kw)
                   speed (speed-with-armor armor)]
               ^{:key armor-kw}
               [:div
                [:div
                 [:span speed]
                 [:span.display-section-qualifier-text (str "(" (:name armor) " armor)")]]]))
           (dissoc all-armor :shield)))]
        (if unarmored-speed-bonus
          [:div
           [:span
            [:span speed]
            [:span.display-section-qualifier-text "(armored)"]]]))
      (let [swim-speed (char/base-swimming-speed built-char)]
        (if swim-speed
          [:div [:span swim-speed] [:span.display-section-qualifier-text "(swim)"]]))
      (let [flying-speed (char/base-flying-speed built-char)]
        (if flying-speed
          [:div [:span flying-speed] [:span.display-section-qualifier-text "(fly)"]]))]]))

(defn list-item-section [list-name icon-name items & [name-fn]]
  [list-display-section list-name icon-name
   (map
    (fn [item]
      ((or name-fn :name) item))
    items)])

(defn compare-spell [spell-1 spell-2]
  (let [key-fn (juxt :key :ability)]
    (compare (key-fn spell-1) (key-fn spell-2))))

(defn details-button [expanded? on-click]
  [:span.orange.underline.pointer
   {:on-click on-click}
   [:span (if expanded? "hide" "details")]
   [:i.fa.m-l-5
    {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]])


(defn spellcaster-levels-table []
  (let [expanded? (r/atom false)]
    (fn [spell-slot-factors total-spellcaster-levels levels mobile?]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:div
         [:span.f-w-b.f-s-16 "Total Spellcaster Levels: "]
         [:span.f-s-16.f-w-n total-spellcaster-levels]]
        (details-button @expanded? #(swap! expanded? not))]
       (if @expanded?
         [:div:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             [:th.p-10 "Class"]
             [:th.p-10 (if mobile? "Sub." "Subclass")]
             [:th.p-10 (if mobile? "Lvl." "Level")]
             [:th.p-10 (if mobile? "Mult." "Multiplier")]
             [:th.p-10 (if mobile? "Tot." "Total")]]
            (doall
             (map
              (fn [[class-key factor]]
                (let [{:keys [class-name class-level subclass-name]} (levels class-key)]
                  ^{:key class-key}
                  [:tr
                   [:td.p-10 class-name]
                   [:td.p-10 subclass-name]
                   [:td.p-10 class-level]
                   [:td.p-10 (if (= 1 factor) 1 (str "1/" factor))]
                   [:td.p-10 (int (/ class-level factor))]]))
              spell-slot-factors))
            [:tr
             [:td.p-10 "Total"]
             [:td]
             [:td]
             [:td]
             [:td.p-10 total-spellcaster-levels]]]]])])))

(defn spell-slots-table []
  (let [expanded? (r/atom false)
        checkboxes-expanded? (r/atom false)]
    (fn [id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]
      (let [multiclass? (> (count spell-slot-factors) 1)
            first-factor-key (if spell-slot-factors (-> spell-slot-factors first key))
            first-class-level (if first-factor-key (-> levels first-factor-key :class-level))]
        [:div.f-s-14.f-w-n
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.f-s-16 (str "Slots" (if multiclass? " (Multiclass)"))]]
          (if (not pact-magic?)
            (details-button @expanded? #(swap! expanded? not)))]
         [:div.f-w-n.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b.f-s-12
             [:th.p-5 (if mobile? "Lvl." "Caster Levels")]
             (doall
              (map
               (fn [i]
                 ^{:key i}
                 [:th.p-5 (if (and mobile?
                                   (or @expanded?
                                       (> (count spell-slots) 8)))
                            (inc i)
                            (common/ordinal (inc i)))])
               (range (if @expanded?
                        9
                        (apply max (keys spell-slots))))))
             [:th]]
            (if (and (not pact-magic?) @expanded?)
              (doall
               (map
                (fn [lvl]
                  (let [highlight? (or (and multiclass?
                                            (= total-spellcaster-levels lvl))
                                       (and (not multiclass?)
                                            (= first-class-level lvl)))]
                    ^{:key lvl}
                    [:tr
                     {:class-name (if highlight?
                                    "f-w-b")
                      :style (if highlight?
                               {:background-color "rgba(255,255,255,0.3)"})}
                     [:td.p-10 lvl]
                     (let [total-slots (opt/total-slots lvl (if multiclass? 1 (-> spell-slot-factors first val)))]
                       (doall
                        (map
                         (fn [spell-lvl]
                           ^{:key spell-lvl}
                           [:td.p-10 (get total-slots spell-lvl)])
                         (range 1 10))))]))
                (range 1 21)))
              [:tr.pointer
               {:on-click #(swap! checkboxes-expanded? not)}
               [:td.p-10 (if multiclass?
                           total-spellcaster-levels
                           first-class-level)]
               (doall
                (map
                 (fn [level]
                   ^{:key level}
                   [:td.p-10 (spell-slots (inc level))])
                 (range (apply max (keys spell-slots)))))
               [:td.p-r-5
                [:i.fa.orange
                 {:class-name (if @checkboxes-expanded? "fa-caret-up" "fa-caret-down")}]]])]]]
         (if @checkboxes-expanded?
           [:div.bg-light.p-5
            (doall
             (map
              (fn [[level slots]]
                ^{:key level}
                [:div.p-10.flex.justify-cont-s-b
                 [:span.f-w-b (str (common/ordinal level) " level")]
                 [:div
                  (doall
                   (for [i (range slots)]
                     ^{:key i}
                     [:span.m-l-5
                      {:on-click #(dispatch [::char/toggle-spell-slot-used id level i])}
                      (comps/checkbox @(subscribe [::char/spell-slot-used? id level i]) false)]))]])
              spell-slots))])]))))

(defn cast-spell-component []
  (let [selected-level (r/atom nil)]
    (fn [id lvl]
      (let [slot-levels-available @(subscribe [::char/slot-levels-available id])
            usable-slot-levels (drop-while
                                (partial > lvl)
                                slot-levels-available)]
        [:div.flex.justify-cont-end.align-items-c
         [:div.w-80
          [:span "Cast at level"]
          [dropdown
           {:items (map
                    (fn [i]
                      {:value i
                       :title i})
                    usable-slot-levels)
            :value (or @selected-level lvl)
            :on-change #(swap! selected-level (js/parseInt %))}]]
         [:div.m-l-5
          [:button.form-button.p-10
           {:class-name (if (empty? usable-slot-levels) "disabled")
            :on-click #(if (seq usable-slot-levels)
                         (dispatch [::char/use-spell-slot id (or @selected-level (first usable-slot-levels))]))}
           "cast spell"]]]))))

(defn spell-row [id lvl spell-modifiers prepares-spells prepared-spells-by-class {:keys [key ability qualifier class always-prepared?]} expanded? on-click]
  (let [spell (spells/spell-map key)
        cls-mods (get spell-modifiers class)
        prepare-spell-count-fn @(subscribe [::char/prepare-spell-count-fn id])
        prepared-spell-count (or (some->> class
                                          (get prepared-spells-by-class)
                                          count)
                                 0)
        remaining-preps (- (prepare-spell-count-fn class)
                           prepared-spell-count)]
    [[:tr.pointer
      {:on-click on-click}
      [:td.p-l-10.p-b-10.p-t-10.f-w-b
       (if (and (pos? lvl)
                (get prepares-spells class))
         [:span.m-r-5
          {:class-name (if always-prepared?
                         "cursor-disabled")
           :on-click (fn [e]
                       (if (not always-prepared?)
                         (dispatch [::char/toggle-spell-prepared id class key]))
                       (.stopPropagation e))}
          (comps/checkbox (or always-prepared?
                              (get-in prepared-spells-by-class [class key]))
                          (or always-prepared?
                              (not (pos? remaining-preps))))])
       (:name spell)]
      [:td.p-l-10.p-b-10.p-t-10 class]
      [:td.p-l-10.p-b-10.p-t-10 (if ability (s/upper-case (name ability)))]
      [:td.p-l-10.p-b-10.p-t-10 (get cls-mods :spell-save-dc)]
      [:td.p-l-10.p-b-10.p-t-10 (common/bonus-str (get cls-mods :spell-attack-modifier))]
      [:td.p-r-10.orange
       [:i.fa
        {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
     (if expanded?
       [:tr {:style {:background-color "rgba(0,0,0,0.1)"}}
        [:td {:col-span 6}
         [:div.p-10
          (if (pos? lvl)
            [cast-spell-component id lvl])
          [spell-component spell false 14]]]])]))

(defn spells-table []
  (let [expanded-spells (r/atom {})
        mobile? @(subscribe [:mobile?])]
    (fn [id lvl spells spell-modifiers hide-unprepared?]
      (let [prepares-spells @(subscribe [::char/prepares-spells id])
            prepared-spells-by-class @(subscribe [::char/prepared-spells-by-class id])]
        [:div.m-t-10.m-b-30
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.i (if (pos? lvl)
                            (str (common/ordinal lvl) " Level")
                            "Cantrip")]
           (if hide-unprepared?
             [:span.i.opacity-5.m-l-5 "(unprepared hidden)"])]
          (if (pos? lvl)
            [:span.f-w-b (str @(subscribe [::char/spell-slots-remaining id lvl]) " remaining")])]
         [:table.w-100-p.t-a-l.striped
          [:tbody
           [:tr.f-w-b.f-s-12
            [:th.p-l-10.p-b-10.p-t-10 (if (and (not (zero? lvl))
                                               (seq prepares-spells))
                                        "Prepared? / Name"
                                        "Name")]
            [:th.p-l-10.p-b-10.p-t-10 (if mobile? "Src" "Source")]
            [:th.p-l-10.p-b-10.p-t-10 (if mobile? "Aby" "Ability")]
            [:th.p-l-10.p-b-10.p-t-10 "DC"]
            [:th.p-l-10
             {:class-name (if (not mobile?) "p-b-10 p-t-10")}
             "Mod."]]
           (doall
            (map-indexed
             (fn [i r]
               (with-meta r {:key i}))
             (mapcat
              (fn [{:keys [key class always-prepared?] :as spell}]
                (let [k (str key class)]
                  (if (char/spell-prepared? {:hide-unprepared? hide-unprepared?
                                             :always-prepared? always-prepared?
                                             :lvl lvl
                                             :key key
                                             :class class
                                             :prepares-spells prepares-spells
                                             :prepared-spells-by-class prepared-spells-by-class})
                    (spell-row id
                               lvl
                               spell-modifiers
                               prepares-spells
                               prepared-spells-by-class
                               spell
                               (@expanded-spells k)
                               #(swap! expanded-spells update k not)))))
              (sort-by :key spells))))]]]))))


(defn spells-tables []
  (let [hide-unprepared? (r/atom false)]
    (fn [id spells-known spell-slots spell-modifiers]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:span.f-w-b.f-s-16 "Spells By Level"]
        [:button.form-button.p-5
         {:on-click #(swap! hide-unprepared? not)}
         (if @hide-unprepared?
           "Show All"
           "Hide Unprepared")]]
       (doall
        (map
         (fn [[lvl spells]]
           ^{:key lvl}
           [spells-table id lvl (vals spells) spell-modifiers @hide-unprepared?])
         spells-known))])))

(defn spells-known-section [id spells-known spell-slots spell-modifiers spell-slot-factors total-spellcaster-levels levels]
  (let [mobile? @(subscribe [:mobile?])
        multiclass? (> (count spell-slot-factors) 1)
        prepares-spells @(subscribe [::char/prepares-spells id])
        pact-magic? @(subscribe [::char/pact-magic? id])
        prepare-spell-count-fn @(subscribe [::char/prepare-spell-count-fn id])]
    [display-section
     "Spells"
     "spell-book"
     [:div.m-t-20
      (if multiclass?
        [:div.m-b-20
         [spellcaster-levels-table spell-slot-factors total-spellcaster-levels levels mobile?]])
      (if (or pact-magic? spell-slot-factors)
        [:div.m-b-20 
         [spell-slots-table id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]])
      [:div.m-b-20
       [:span.f-w-b.f-s-16 "Spell Preparation"]
       (if (seq prepares-spells)
         [:table.w-100-p.t-a-l.striped.f-s-12
          [:tbody
           [:tr.f-w-b
            [:th.p-10 "Class"]
            [:th.p-10 "Can Prepare"]]
           (doall
            (map
             (fn [[class-nm]]
               ^{:key class-nm}
               [:tr.f-w-n
                [:td.p-10 class-nm]
                [:td.p-10 (str (prepare-spell-count-fn class-nm) "/day")]])
             prepares-spells))]]
         [:div.f-s-14.f-w-n.i.m-t-5 "You don't need to prepare spells"])]
      [:div.m-b-20
       [spells-tables id spells-known spell-slots spell-modifiers]]]
     nil
     [[finish-long-rest-button id]]]))

(defn equipment-section [title icon-name equipment equipment-map]
  [list-display-section title icon-name
   (map
    (fn [[equipment-kw {item-qty ::char-equip/quantity
                        equipped? ::char-equip/equipped?
                        :as num}]]
      (str (disp/equipment-name equipment-map equipment-kw)
           " (" (or item-qty num) ")"))
    equipment)])

(defn add-links [desc]
  desc
  (let [{:keys [abbr url]} (some (fn [[_ source]]
                                   (if (and (:abbr source)
                                            (re-matches (re-pattern (str ".*" (:abbr source) ".*")) desc))
                             source))
                 disp/sources)
        [before after] (if abbr (s/split desc (re-pattern abbr)))]
    (if abbr
      [:span
       [:span before]
       [:a {:href url :target :_blank} abbr]
       [:span after]]
      desc)))

(defn attacks-section [attacks]
  (if (seq attacks)
    (display-section
     "Attacks"
     "pointy-sword"
     [:div.f-s-14
      (doall
       (map
        (fn [{:keys [name area-type description damage-die damage-die-count damage-type save save-dc] :as attack}]
          ^{:key name}
          [:p.m-t-10
           [:span.f-w-600.i name "."]
           [:span.f-w-n.m-l-10 (add-links (common/sentensize (disp/attack-description attack)))]])
        attacks))])))

(defn actions-section [title icon-name actions]
  (if (seq actions)
    (display-section
     title icon-name
     [:div.f-s-14.l-h-19
      (doall
       (map
        (fn [{{:keys [units amount]} :frequency nm :name :as action}]
          ^{:key action}
          [:p.m-t-10
           [:span.f-w-600.i nm "."]
           [:span.f-w-n.m-l-10 (add-links (common/sentensize (disp/action-description action)))]
           (if (and amount units)
             [:span.m-l-10
              (doall
               (for [i (range amount)]
                 (let [k (str nm "-" i)]
                   ^{:key i}
                   [:span
                    {:on-click #(dispatch [::char/toggle-feature-used id units k])}
                    [:span.m-r-5 (comps/checkbox @(subscribe [::char/feature-used? id units k]) false)]])))])])
        (sort-by :name actions)))])))

(defn prof-name [prof-map prof-kw]
  (or (-> prof-kw prof-map :name) (common/kw-to-name prof-kw)))

(defn resistance-str [{:keys [value qualifier]}]
  (str (name value)
       (if qualifier (str " (" qualifier ")"))))

(def no-https-images "Sorry, we don't currently support images that start with https")

(defn default-image [race classes]
  (if (and (or (= "Human" race)
               (nil? race))
           (= :barbarian (first classes)))
    "/image/barbarian.png"))

(defn section-header-2 [title icon]
  [:div
   (if icon (svg-icon icon 24))
   [:div.f-s-18.f-w-b.m-b-5 title]])

(defn armor-class-section-2 [id]
  (let [unarmored-armor-class @(subscribe [::char/armor-class id])
        ac-with-armor-fn @(subscribe [::char/armor-class-with-armor id])
        all-armor-inventory (mi/equipped-armor-details @(subscribe [::char/all-armor-inventory id]))
        equipped-armor (armor/non-shields all-armor-inventory)
        equipped-shields (armor/shields all-armor-inventory)]
    [:div
     [:div.p-10.flex.flex-column.align-items-c
      (section-header-2 "Armor Class" "checked-shield")
      [:div.f-s-24.f-w-b (char/max-armor-class unarmored-armor-class
                                               ac-with-armor-fn
                                               all-armor-inventory
                                               equipped-armor
                                               equipped-shields)]]]))

(defn basic-section [title icon v]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 title icon)
    [:div.f-s-24.f-w-b v]]])

(def current-hit-points-editor-style
  {:width "60px"
   :margin-top 0})

(defn hit-dice-section-2 [id]
  (let [levels @(subscribe [::char/levels id])]
    (basic-section "Hit Dice"
                   nil
                   (s/join
                    " / "
                    (map
                     (fn [{:keys [class-level hit-die]}] (str class-level "d" hit-die))
                     (vals levels))))))

(defn hit-points-section-2 [id]
  (basic-section "Max Hit Points"
                 "health-normal"
                 [:div.flex.align-items-c
                  [:input.input
                   {:style current-hit-points-editor-style
                    :type :number
                    :value (or @(subscribe [::char/current-hit-points id])
                               @(subscribe [::char/max-hit-points id]))
                    :on-change #(dispatch [::char/set-current-hit-points
                                           id
                                           (or (-> %
                                                event-value
                                                js/parseInt)
                                               0)])}]
                  [:span.m-l-5 "/"]
                  [:span.m-l-5 @(subscribe [::char/max-hit-points id])]]))

(defn initiative-section-2 [id]
  (basic-section "Initiative" "sprint" (common/bonus-str @(subscribe [::char/initiative id]))))

(defn darkvision-section-2 [id]
  (basic-section "Darkvision" "night-vision" (str @(subscribe [::char/darkvision id]) " ft.")))

(defn critical-hits-section-2 [id]
  (let [crit-values-str @(subscribe [::char/crit-values-str id])]
    (basic-section "Critical Hits" nil crit-values-str)))

(defn number-of-attacks-section-2 [id]
  (basic-section "Number of Attacks" nil @(subscribe [::char/number-of-attacks id])))

(defn passive-perception-section-2 [id]
  (basic-section "Passive Perception" "awareness" @(subscribe [::char/passive-perception id])))

(defn proficiency-bonus-section-2 [id]
  (basic-section "Proficiency Bonus" nil (common/bonus-str @(subscribe [::char/proficiency-bonus id]))))

(defn skills-section-2 [id]
  (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
        skill-bonuses @(subscribe [::char/skill-bonuses id])]
    [:div
     [proficiency-bonus-section-2 id]
     [passive-perception-section-2 id]
     [:div.p-10.flex.flex-column.align-items-c
      (section-header-2 "Skills" "juggler")
      [:table
       [:tbody
        (doall
         (map
          (fn [{skill-name :name skill-key :key icon :icon :as skill}]
            ^{:key skill-key}
            [:tr.t-a-l
             {:class-name (if (skill-profs skill-key) "f-w-b" "opacity-7")}
             [:td [:div
                   (svg-icon icon 18)
                   [:span.m-l-5 skill-name]]]
             [:td [:div.p-5 (common/bonus-str (skill-bonuses skill-key))]]])
          skills/skills))]]]]))

(defn ability-scores-section-2 [id]
  (let [abilities @(subscribe [::char/abilities id])
        ability-bonuses @(subscribe [::char/ability-bonuses id])
        theme @(subscribe [:theme])]
    [:div
     [:div.f-s-18.f-w-b "Ability Scores"]
     [:div.flex.justify-cont-s-a.m-t-10
      (doall
       (map
        (fn [k]
          ^{:key k}
          [:div
           (t/ability-icon k 24 theme)
           [:div
            [:span.f-s-20.uppercase (name k)]]
           [:div.f-s-24.f-w-b (abilities k)]
           [:div.f-s-12.opacity-5.m-b--2.m-t-2 "mod"]
           [:div.f-s-18 (common/bonus-str (ability-bonuses k))]])
        char/ability-keys))]]))

(defn saving-throws-section-2 [id]
  (let [save-bonuses @(subscribe [::char/save-bonuses id])
        saving-throws @(subscribe [::char/saving-throws id])
        theme @(subscribe [:theme])]
    [:div.p-10.flex.flex-column.align-items-c
     (section-header-2 "Saving Throws" "dodging")
     [:table
      [:tbody
       (doall
        (map
         (fn [k]
           ^{:key k}
           [:tr.t-a-l
            {:class-name (if (saving-throws k) "f-w-b" "opacity-7")}
            [:td [:div
                  (t/ability-icon k 18 theme)
                  [:span.m-l-5 (s/upper-case (name k))]]]
            [:td [:div.p-5 (common/bonus-str (save-bonuses k))]]])
         char/ability-keys))]]]))

(defn feet-str [num]
  (str num " ft."))

(defn speed-section-2 [id]
  (let [speed @(subscribe [::char/base-land-speed id])
        swim-speed @(subscribe [::char/base-swimming-speed id])
        flying-speed @(subscribe [::char/base-flying-speed id])
        speed-with-armor @(subscribe [::char/speed-with-armor id])
        unarmored-speed-bonus @(subscribe [::char/unarmored-speed-bonus id])
        equipped-armor @(subscribe [::char/armor id])
        all-armor @(subscribe [::char/all-armor-inventory id])]
    [:div.p-10
     (section-header-2 "Speed" "walking-boot")
     [:span.f-s-24.f-w-b
      [:span
       [:span (feet-str (+ (or unarmored-speed-bonus 0)
                      (if speed-with-armor
                        (speed-with-armor nil)
                        speed)))]
       (if (or unarmored-speed-bonus
               speed-with-armor)
         [:span.display-section-qualifier-text "(unarmored)"])]
      (if speed-with-armor
        [:div.f-s-18
         (doall
          (map
           (fn [[armor-kw _]]
             (let [armor (mi/all-armor-map armor-kw)
                   speed (speed-with-armor armor)]
               ^{:key armor-kw}
               [:div
                [:div
                 [:span (feet-str speed)]
                 [:span.display-section-qualifier-text (str "(" (:name armor) " armor)")]]]))
           (dissoc all-armor :shield)))]
        (if unarmored-speed-bonus
          [:div.f-s-18
           [:span
            [:span (feet-str speed)]
            [:span.display-section-qualifier-text "(armored)"]]]))
      (if (and swim-speed (pos? swim-speed))
        [:div.f-s-18 [:span (feet-str swim-speed)] [:span.display-section-qualifier-text "(swim)"]])
      (if (and flying-speed (pos? flying-speed))
        [:div.f-s-18 [:span (feet-str flying-speed)] [:span.display-section-qualifier-text "(fly)"]])]]))

(defn personality-section [title & descriptions]
  (if (and (seq descriptions)
           (some (complement s/blank?) descriptions))
    [:div.m-t-20.t-a-l
     [:div.f-w-b.f-s-18 title]
     [:div
      (doall
       (map-indexed
        (fn [i description]
          ^{:key i}
          [:div
           (doall
            (map-indexed
             (fn [j p]
               ^{:key j}
               [:p p])
             (s/split
              description
              #"\n")))])
        descriptions))]]))

(defn description-section [id]
  (let [personality-trait-1 @(subscribe [::char/personality-trait-1 id])
        personality-trait-2 @(subscribe [::char/personality-trait-2 id])
        ideals @(subscribe [::char/ideals id])
        bonds @(subscribe [::char/bonds id])
        flaws @(subscribe [::char/flaws id])
        description @(subscribe [::char/description id])]
    [:div.p-5
     (personality-section "Personality Traits" personality-trait-1 personality-trait-2)
     (personality-section "Ideals" ideals)
     (personality-section "Bonds" bonds)
     (personality-section "Flaws" flaws)
     (personality-section "Description" description)]))

(def notes-style
  {:height "400px"
   :width "100%"})

(defn summary-details [num-columns id]
  (let [built-char @(subscribe [:built-character id])
        race @(subscribe [::char/race id])
        classes @(subscribe [::char/classes id])
        background @(subscribe [::char/background id])
        alignment @(subscribe [::char/alignment id])
        all-armor @(subscribe [::char/all-armor id])
        image-url-failed @(subscribe [::char/image-url-failed id])
        image-url @(subscribe [::char/image-url id])
        faction-image-url @(subscribe [::char/faction-image-url id])
        faction-image-url-failed @(subscribe [::char/faction-image-url-failed id])
        armor-class @(subscribe [::char/armor-class id])
        armor-class-with-armor @(subscribe [::char/armor-class-with-armor id])]
    [:div
     [:div
      [:div.w-100-p.t-a-c
       [:div
        [ability-scores-section-2 id]
        [:div.flex.p-10.justify-cont-s-a
         [skills-section-2 id]
         [:div
          [armor-class-section-2 id]
          [hit-points-section-2 id]
          [speed-section-2 id]
          [saving-throws-section-2 id]
          [darkvision-section-2 id]]]
        [description-section id]
        [:span.f-s-18.f-w-b.m-b-5 "Notes"]
        [:div.p-l-20.p-r-20
         [:div.w-100-p
          [comps/input-field
           :textarea
           @(subscribe [::char/notes id])
           #(dispatch [::char/set-notes id %])
           {:style notes-style
            :class-name "input"}]]]]]]]))

(defn weapon-details-field [nm value]
  [:div.p-2
   [:span.f-w-b nm ":"]
   [:span.m-l-5 value]])

(defn yes-no [v]
  (if v "yes" "no"))

(defn weapon-details [{:keys [::weapon/description
                              ::weapon/type
                              ::weapon/damage-type
                              ::mi/magical-damage-bonus
                              ::mi/magical-attack-bonus
                              ::weapon/ranged?
                              ::weapon/melee?
                              ::weapon/range
                              ::weapon/two-handed?
                              ::weapon/finesse?
                              ::weapon/link
                              ::weapon/versatile
                              ::weapon/thrown]
                       :as weapon}
                      damage-modifier-fn]
  [:div.m-t-10.i
   (weapon-details-field "Type" (common/safe-name type))
   (weapon-details-field "Damage Type" (common/safe-name damage-type))
   (if magical-damage-bonus
     (weapon-details-field "Magical Damage Bonus" magical-damage-bonus))
   (if magical-attack-bonus
     (weapon-details-field "Magical Attack Bonus" magical-attack-bonus))
   (weapon-details-field "Melee/Ranged" (if melee? "melee" "ranged"))
   (if range
     (weapon-details-field "Range" (str (::weapon/min range) "/" (::weapon/max range) " ft.")))
   (weapon-details-field "Finesse?" (yes-no finesse?))
   (weapon-details-field "Two-handed?" (yes-no two-handed?))
   (weapon-details-field "Versatile" (if versatile
                                       (str (::weapon/damage-die-count versatile)
                                            "d"
                                            (::weapon/damage-die versatile)
                                            (common/mod-str (damage-modifier-fn weapon false))
                                            " damage")
                                       "no"))
   (if description
     [:div.m-t-10 description])])

(defn armor-details-section [{:keys [type
                                     base-ac
                                     weight
                                     description
                                     max-dex-mod
                                     min-str
                                     ::mi/magical-ac-bonus
                                     stealth-disadvantage?]
                              :or {magical-ac-bonus 0
                                   base-ac 10}}
                             {shield-magic-bonus ::magical-ac-bonus :or {shield-magic-bonus 0} :as shield}
                             expanded?]
  [:div
   [:div (str (if type (str (common/safe-name type) ", ")) "base AC " (+ magical-ac-bonus shield-magic-bonus base-ac (if shield 2 0)) (if stealth-disadvantage? ", stealth disadvantage"))]
   (if expanded?
     [:div
      [:div.m-t-10.i
       (if type
         (weapon-details-field "Type" (common/safe-name type)))
       (weapon-details-field "Base AC" base-ac)
       (if (not= magical-ac-bonus 0)
         (weapon-details-field "Magical AC Bonus" magical-ac-bonus))
       (if shield
         (weapon-details-field "Shield Base AC Bonus" 2))
       (if (and shield
                (not= shield-magic-bonus 0))
         (weapon-details-field "Shield Magical AC Bonus" shield-magic-bonus))
       (if max-dex-mod
         (weapon-details-field "Max DEX AC Bonus" max-dex-mod))
       (if min-str
         (weapon-details-field "Min Strength" min-str))
       (weapon-details-field "Stealth Disadvantage?" (yes-no stealth-disadvantage?))
       (if weight
         (weapon-details-field "Weight" (str weight " lbs.")))
       (if description
         [:div.m-t-10 (str "Armor: " description)])
       (if (:description shield)
         [:div.m-t-10 (str "Shield: " (:description shield))])]])])

(defn boolean-icon [v]
  [:i.fa {:class-name (if v "fa-check green" "fa-times red")}])

(defn armor-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-armor @(subscribe [::char/all-armor id])
            ac-with-armor @(subscribe [::char/armor-class-with-armor id])
            armor-profs (set @(subscribe [::char/armor-profs id]))
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-armor-details (map @(subscribe [::mi/all-armor-map]) (keys all-armor))
            armor-details (armor/non-shields all-armor-details)
            shield-details (armor/shields all-armor-details)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "breastplate" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Armor"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (if (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th]
             [:th.p-10 "AC"]]
            (doall
             (for [{:keys [name description type key] :as armor} (conj armor-details nil)
                   shield (conj shield-details nil)]
               (let [k (str key (:key shield))
                     ac (ac-with-armor armor shield)
                     proficient? (and
                                  (or (nil? shield)
                                      (armor-profs :shields))
                                  (or
                                   (nil? armor)
                                   (armor-profs key)
                                   (armor-profs type)))
                     expanded? (@expanded-details k)]
                 ^{:key (str key (:key shield))}
                 [:tr.pointer
                  {:on-click #(swap! expanded-details (fn [d] (update d k not)))}
                  [:td.p-10.f-w-b (str (or (::mi/name armor) (:name armor) "unarmored")
                                       (if shield (str " + " (:name shield))))]
                  (if (not mobile?)
                    [:td.p-10 (boolean-icon proficient?)])
                  [:td.p-10.w-100-p
                   [:div
                    (armor-details-section armor shield expanded?)]]
                  [:td
                   [:div.orange
                    (if (not mobile?)
                      [:span.underline (if expanded? "less" "more")])
                    [:i.fa.m-l-5
                     {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                  [:td.p-10.f-w-b.f-s-18 ac]])))]]]]))))

(defn weapons-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-weapons @(subscribe [::char/all-weapons id])
            weapon-profs (set @(subscribe [::char/weapon-profs id]))
            weapon-attack-modifier @(subscribe [::char/weapon-attack-modifier-fn id])
            weapon-damage-modifier @(subscribe [::char/weapon-damage-modifier-fn id])
            has-weapon-prof @(subscribe [::char/has-weapon-prof id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-weapons-map @(subscribe [::mi/all-weapons-map])]
        [:div
         [:div.flex.align-items-c
          (svg-icon "crossed-swords" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Weapons"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (if (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th]
             [:th.p-10 (if mobile? "Atk" [:div.w-40 "Attack Bonus"])]]
            (doall
             (map
              (fn [[weapon-key {:keys [equipped?]}]]
                (let [{:keys [name description ranged?] :as weapon} (all-weapons-map weapon-key)
                      proficient? (has-weapon-prof weapon)
                      expanded? (@expanded-details weapon-key)
                      damage-modifier (max (weapon-damage-modifier weapon false)
                                           (weapon-damage-modifier weapon true))]
                  ^{:key weapon-key}
                  [:tr.pointer
                   {:on-click #(swap! expanded-details (fn [d] (update d weapon-key not)))}
                   [:td.p-10.f-w-b (or (:name weapon)
                                       (::mi/name weapon))]
                   (if (not mobile?)
                     [:td.p-10 (boolean-icon proficient?)])
                   [:td.p-10.w-100-p
                    [:div
                     (disp/attack-description (-> weapon
                                                  (assoc :attack-type (if ranged? :ranged :melee))
                                                  (assoc :damage-modifier damage-modifier)
                                                  (dissoc :description)))]
                    (if expanded?
                      (weapon-details weapon weapon-damage-modifier))]
                   [:td
                    [:div.orange
                     (if (not mobile?)
                       [:span.underline (if expanded? "less" "more")])
                     [:i.fa.m-l-5
                      {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                   [:td.p-10.f-w-b.f-s-18 (common/bonus-str (max (weapon-attack-modifier weapon true)
                                                                 (weapon-attack-modifier weapon false)))]]))
              all-weapons))]]]]))))

(defn magic-item-rows [expanded-details magic-item-cfgs magic-weapon-cfgs magic-armor-cfgs]
  (let [magic-item-map @(subscribe [::mi/all-magic-items-map])]
    (mapcat
     (fn [[item-kw item-cfg]]
       (let [{:keys [::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement ::mi/description ::mi/summary] :as item} (magic-item-map item-kw)
             expanded? (@expanded-details item-kw)]
         [[:tr.pointer
           {:on-click #(swap! expanded-details (fn [d] (update d item-kw not)))}
           [:td.p-10.f-w-b (or (:name item) name)]
           [:td.p-10 (str (common/kw-to-name type)
                          ", "
                          (common/kw-to-name rarity))]
           [:td.p-r-5
            [:div.orange
             (if (not mobile?)
               [:span.underline (if expanded? "less" "more")])
             [:i.fa.m-l-5
              {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]]
          (if expanded?
            [:tr
             [:td.p-10
              {:col-span 3}
              [item-component item true true]]])]))
     (merge
      magic-item-cfgs
      magic-weapon-cfgs
      magic-armor-cfgs))))

(defn magic-items-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            magic-item-cfgs @(subscribe [::char/magic-items id])
            magic-weapon-cfgs @(subscribe [::char/magic-weapons id])
            magic-armor-cfgs @(subscribe [::char/magic-items id])]
        [:div
         [:div.flex.align-items-c
          (svg-icon "orb-wand" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Magic Items"]]
         [:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map-indexed
              (fn [i row]
                (with-meta
                  row
                  {:key i}))
              (magic-item-rows expanded-details
                               magic-item-cfgs
                               magic-weapon-cfgs
                               magic-armor-cfgs)))]]]]))))

(defn other-equipment-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            equipment-cfgs (merge
                            @(subscribe [::char/equipment id])
                            (zipmap (range) @(subscribe [::char/custom-equipment id])))]
        [:div
         [:div.flex.align-items-c
          (svg-icon "backpack" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Equipment"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Qty."]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map
              (fn [[item-kw item-cfg]]
                (let [item-name (::char-equip/name item-cfg)
                      {:keys [name cost weight] :as item} (equip/equipment-map item-kw)
                      expanded? (@expanded-details item-kw)]
                  ^{:key item-kw}
                  [:tr.pointer
                   {:on-click #(swap! expanded-details (fn [d] (update d item-kw not)))}
                   [:td.p-10.f-w-b (or (:name item) item-name)]
                   [:td.p-10 (::char-equip/quantity item-cfg)]
                   [:td.p-10
                    [:div
                     [:div
                      (str (if cost
                             (str (:num cost)
                                  " "
                                  (common/safe-name (:type cost))
                                  ", "))
                           weight)]]]]))
              equipment-cfgs))]]]]))))


(defn skill-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
            skill-bonuses @(subscribe [::char/skill-bonuses id])
            skill-expertise @(subscribe [::char/skill-expertise id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "juggler" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Skills"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:td.p-10 (if mobile? "Prof?" "Proficient?")]
             (if skill-expertise
               [:th.p-10 "Expertise?"])
             [:th.p-10 (if (not mobile?) [:div.w-40 "Bonus"])]]
            (doall
             (map
              (fn [{:keys [key name]}]
                (let [proficient? (key skill-profs)
                      expertise? (key skill-expertise)]
                  ^{:key key}
                  [:tr
                   [:td.p-10.f-w-b name]
                   [:td.p-10 (boolean-icon proficient?)]
                   (if skill-expertise
                     [:td.p-10 (boolean-icon expertise?)])
                   [:td.p-10.f-s-18.f-w-b (common/bonus-str (key skill-bonuses))]]))
              skills/skills))]]]]))))

(defn tool-prof-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [tool-profs (or @(subscribe [::char/tool-profs id]) #{})
            tool-expertise @(subscribe [::char/tool-expertise id])
            tool-bonus-fn @(subscribe [::char/tool-bonus-fn id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        (if (seq tool-profs)
          [:div
           [:div.flex.align-items-c
            (svg-icon "stone-crafting" 32)
            [:span.m-l-5.f-w-b.f-s-18 "Tools"]]
           [:div
            [:table.w-100-p.t-a-l.striped
             [:tbody
              [:tr.f-w-b
               {:class-name (if mobile? "f-s-12")}
               [:th.p-10 "Name"]
               [:td.p-10 (if mobile? "Prof?" "Proficient?")]
               (if tool-expertise
                 [:th.p-10 "Expertise?"])
               [:th.p-10 (if (not mobile?) [:div.w-40 "Bonus"])]]
              (doall
               (map
                (fn [[kw]]
                  (let [name (-> equip/tools-map kw :name)
                        proficient? (kw tool-profs)
                        expertise? (kw tool-expertise)]
                    ^{:key kw}
                    [:tr
                     [:td.p-10.f-w-b name]
                     [:td.p-10 (boolean-icon proficient?)]
                     (if tool-expertise
                       [:td.p-10 (boolean-icon expertise?)])
                     [:td.p-10.f-s-18.f-w-b (common/bonus-str (tool-bonus-fn kw))]]))
                tool-profs))]]]])))))


(defn proficiency-details [num-columns id]
  (let [ability-bonuses @(subscribe [::char/ability-bonuses id])]
    [:div.details-columns
     {:class-name (if (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      [skill-details-section-2 id]
      [:div.m-t-20
       [tool-prof-details-section-2 id]]
      [list-item-section "Languages" "lips" @(subscribe [::char/languages id]) (partial prof-name opt/language-map)]
      [list-item-section "Tool Proficiencies" "stone-crafting" @(subscribe [::char/tool-profs id]) (fn [[kw]] (prof-name equip/tools-map kw))]
      [list-item-section "Weapon Proficiencies" "bowman" @(subscribe [::char/weapon-profs id]) (partial prof-name weapon/weapons-map)]
      [list-item-section "Armor Proficiencies" "mailed-fist" @(subscribe [::char/armor-profs id]) (partial prof-name armor/armor-map)]]]))

(defn combat-details [num-columns id]
  (let [weapon-profs @(subscribe [::char/weapon-profs id])
        armor-profs @(subscribe [::char/armor-profs id])
        resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        weapons @(subscribe [::char/weapons id])
        armor @(subscribe [::char/armor id])
        magic-weapons @(subscribe [::char/magic-weapons id])
        magic-armor @(subscribe [::char/magic-armor id])
        attacks @(subscribe [::char/attacks id])
        critical-hit-values @(subscribe [::char/critical-hit-values id])
        non-standard-crits? (> (count critical-hit-values) 1)
        number-of-attacks @(subscribe [::char/number-of-attacks id])
        non-standard-attack-number? (> number-of-attacks 1)]
    [:div
     [:div.flex.justify-cont-s-a.t-a-c
      [armor-class-section-2 id]
      [hit-points-section-2 id]
      [speed-section-2 id]
      [initiative-section-2 id]]
     (if (or non-standard-crits?
             non-standard-attack-number?)
       [:div.flex.justify-cont-s-a.t-a-c
        [critical-hits-section-2 id]
        [hit-dice-section-2 id]
        [number-of-attacks-section-2 id]])
     [:div.m-t-30
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Immunities" nil immunities resistance-str]]
     [:div.m-t-30
      [attacks-section attacks]]
     [:div.m-t-30
      [weapons-section-2 id]]
     [:div.m-t-30
      [armor-section-2 id]]
     [:div
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Weapon Proficiencies" "bowman" weapon-profs (partial prof-name weapon/weapons-map)]
      [list-item-section "Armor Proficiencies" "mailed-fist" armor-profs (partial prof-name armor/armor-map)]]]))

(defn finish-long-rest-button [id]
  [:button.form-button.p-5
   {:on-click #(dispatch [::char/finish-long-rest id])}
   "finish long rest"])

(defn features-details [num-columns id]
  (let [resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        actions @(subscribe [::char/actions id])
        bonus-actions @(subscribe [::char/bonus-actions id])
        reactions @(subscribe [::char/reactions id])
        traits @(subscribe [::char/traits id])
        attacks @(subscribe [::char/attacks id])
        all-traits (concat actions bonus-actions reaction traits attacks)
        freqs (into #{} (map #(some-> % :frequency :units) all-traits))]
    [:div.details-columns
     {:class-name (if (= 2 num-columns) "flex")}
   
     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]
      [list-item-section "Immunities" nil immunities resistance-str]
      [:div.flex.justify-cont-end.align-items-c
       (if (or (freqs ::units/long-rest)
               (freqs ::units/rest))
         [finish-long-rest-button id])
       (if (or (freqs ::units/short-rest)
               (freqs ::units/rest))
         [:button.form-button.p-5.m-l-5
          {:on-click #(dispatch [::char/finish-short-rest id])}
          "finish short rest"])
       (if (freqs ::units/round)
         [:button.form-button.p-5.m-l-5
          {:on-click #(dispatch [::char/new-round id])}
          "new round"])
       (if (freqs ::units/turn)
         [:button.form-button.p-5.m-l-5
          {:on-click #(dispatch [::char/new-turn id])}
          "new turn"])]
      [attacks-section attacks]
      [actions-section "Actions" "beams-aura" actions]
      [actions-section "Bonus Actions" "run" bonus-actions]
      [actions-section "Reactions" "van-damme-split" reactions]
      [actions-section "Features, Traits, and Feats" "vitruvian-man" traits]]]))

(defn spell-details [num-columns id]
  (let [spells-known @(subscribe [::char/spells-known id])
        spell-slots @(subscribe [::char/spell-slots id])
        spell-modifiers @(subscribe [::char/spell-modifiers id])
        spell-slot-factors @(subscribe [::char/spell-slot-factors id])
        total-spellcaster-levels @(subscribe [::char/total-spellcaster-levels id])
        levels @(subscribe [::char/levels id])]
    [:div.details-columns
     {:class-name (if (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      (if (seq spells-known) [spells-known-section
                              id
                              spells-known
                              spell-slots
                              spell-modifiers
                              spell-slot-factors
                              total-spellcaster-levels
                              levels])]]))

(defn equipment-details [num-columns id]
  [:div
   [:div.m-t-10
    [weapons-section-2 id]]
   [:div.m-t-30
    [armor-section-2 id]]
   [:div.m-t-30
    [magic-items-section-2 id]]
   [:div.m-t-30
    [other-equipment-section-2 id]]])

(defn details-tab [title icon device-type selected? on-select]
  [:div.b-b-2.f-w-b.pointer.p-10.hover-opacity-full
   {:class-name (if selected? "b-orange" "b-gray")
    :on-click on-select}
   [:div.hover-opacity-full
    {:class-name (if (not selected?) "opacity-5")}
    [:div (svg-icon icon 24)]
    (if (= device-type :desktop)
      [:div.uppercase.f-s-10
       title])]])


(def details-tabs
  {"summary" {:icon "stabbed-note"
              :view summary-details}
   "combat" {:icon "sword-clash"
             :view combat-details}
   "proficiencies" {:icon "juggler"
                    :view proficiency-details}
   "spells" {:icon "spell-book"
             :view spell-details}
   "features" {:icon "vitruvian-man"
               :view features-details}
   "equipment" {:icon "backpack"
                :view equipment-details}})

(defn character-display [id show-summary? num-columns]
  (let [device-type @(subscribe [:device-type])
        selected-tab @(subscribe [::char/selected-display-tab])]
    (let [two-columns? (= 2 num-columns)
          tab (if selected-tab
                selected-tab
                (if two-columns?
                  "combat"
                  "summary"))]
      [:div.w-100-p
       [:div
        (if show-summary?
          [:div.f-s-24.f-w-600.m-b-16.m-l-20.text-shadow.flex
           [character-summary id true true]])
        [:div.flex.w-100-p
         (if two-columns?
           [:div.w-50-p
            [summary-details num-columns id]])
         [:div
          {:class-name (if two-columns? "w-50-p" "w-100-p")}
          [:div.flex.p-l-10.m-b-10.m-r-10
           (doall
            (map
             (fn [[title {:keys [view icon]}]]
               ^{:key title}
               [:div.flex-grow-1.t-a-c
                [details-tab
                 title
                 icon
                 device-type
                 (= title tab)
                 #(dispatch [::char/set-selected-display-tab title])]])
             (if two-columns?
               (rest details-tabs)
               details-tabs)))]
          [(-> tab details-tabs :view) num-columns id]]]]])))

(defn share-link [id]
  [:a.m-r-5.f-s-14
   {:href (str "mailto:?subject=My%20OrcPub%20Character%20"
               @(subscribe [::char/character-name id])
               "&body=https://"
               js/window.location.hostname
               (routes/path-for routes/dnd-e5-char-page-route :id id))}
   [:i.fa.fa-envelope.m-r-5]
   "share"])

(def character-display-style
  {:padding "20px 5px"
   :background-color "rgba(0,0,0,0.15)"})

(defn add-to-party-component []
  (let [party-id (r/atom nil)]
    (fn [character-id]
      [:div.m-l-10.f-w-b
       [:span "Add to Party:"]
       [:div.flex
        [:select.builder-option.builder-option-dropdown
         {:on-change (fn [e] (let [value (event-value e)
                                   id (if (not (s/blank? value))
                                        (js/parseInt value))]
                               (if id
                                 (reset! party-id id))))}
         [:option.builder-dropdown-item
          "<new party>"]
         (doall
          (map
           (fn [{:keys [:db/id ::party/name]}]
             ^{:key id}
             [:option.builder-dropdown-item
              {:value id}
              name])
           @(subscribe [::party/parties])))]
        [:button.form-button.m-t-5.m-l-5
         {:on-click #(if @party-id
                       (dispatch [::party/add-character-remote @party-id character-id true])
                       (dispatch [::party/make-party #{character-id}]))}
         "ADD"]]])))

(defn print-options []
  (let [print-character-sheet? @(subscribe [::char/print-character-sheet?])
        print-spell-cards? @(subscribe [::char/print-spell-cards?])
        print-prepared-spells? @(subscribe [::char/print-prepared-spells?])]
    [:div.flex.justify-cont-end
     [:div.p-20
      [:div.f-s-24.f-w-b.m-b-10 "Print Options"]
      [:div.m-b-20
       #_[:div.m-b-10
        [:span.f-w-b "Parts"]]
       [:div.flex
        #_[:div
         {:on-click #(dispatch [::char/toggle-character-sheet-print])}
         [labeled-checkbox
          "Character Sheet"
          print-character-sheet?]]
        [:div
         {:on-click #(dispatch [::char/toggle-spell-cards-print])}
         [labeled-checkbox
          "Print Spell Cards"
          print-spell-cards?]]]]
      [:div.m-b-10
       [:div.m-b-10
        [:span.f-w-b "Spells Printed"]]
       [:div.flex
        [:div
         {:on-click #(dispatch [::char/toggle-known-spells-print])}
         [labeled-checkbox
          "Known"
          (not print-prepared-spells?)]]
        [:div.m-l-20
         {:on-click #(dispatch [::char/toggle-known-spells-print])}
         [labeled-checkbox
          "Prepared"
          print-prepared-spells?]]]]
      [:span.orange.underline.pointer.uppercase.f-s-12
       {:on-click #(dispatch [::char/hide-options])}
       "Cancel"]
      [:button.form-button.p-10.m-l-5
       {:on-click #(let [export-fn (export-pdf @(subscribe [:built-character])
                                              {:print-character-sheet? print-character-sheet?
                                               :print-spell-cards? print-spell-cards?
                                               :print-prepared-spells? print-prepared-spells?})]
                     (export-fn)
                     (dispatch [::char/hide-options]))}
       "Print"]]]))

(defn make-print-handler [built-char]
  (if (seq (char/spells-known built-char))
    #(dispatch
     [::char/show-options
      [print-options]])
    (export-pdf built-char
                {:print-character-sheet? true
                 :print-spell-cards? false
                 :print-prepared-spells? false})))

(defn character-page [{:keys [id] :as arg}]
  (let [{:keys [::entity/owner] :as character} @(subscribe [::char/character id])
        built-template (subs/built-template
                        @(subscribe [::char/template])
                        (subs/selected-plugin-options
                         character))
        built-character (subs/built-character character built-template)
        device-type @(subscribe [:device-type])
        username @(subscribe [:username])]
    [content-page
     "Character Page"
     (remove
      nil?
      [[share-link id]
       [character-page-fb-button id]
       (if (and username
                owner
                (= owner username))
         {:title "Edit"
          :icon "pencil"
          :on-click #(dispatch [:edit-character character])})
       {:title "Print"
        :icon "print"
        :on-click (make-print-handler built-character)}
       (if (and username owner (not= owner username))
         [add-to-party-component (js/parseInt id)])])
     [:div.p-10.main-text-color
      [character-display id true (if (= :mobile device-type) 1 2)]]]))

(defn monster-page [{:keys [key] :as arg}]
  (let [monster (monsters/monster-map (common/name-to-kw key))]
    [content-page
     "Monster Page"
     []
     [:div.p-10.main-text-color
      [monster-component monster]]]))

(defn spell-page [{:keys [key] :as arg}]
  (let [spell (spells/spell-map (common/name-to-kw key))]
    [content-page
     "Spell Page"
     (remove
      nil?
      [])
     [:div.p-10.main-text-color
      [spell-component spell true]]]))

(defn item-page [{:keys [key] :as arg}]
  (let [item-key (if (re-matches #"\d+" key)
                   (js/parseInt key)
                   (keyword key))
        item @(subscribe [::mi/item item-key])
        username @(subscribe [:username])
        owner? (= username (::mi/owner item))]
    [content-page
     "Item Page"
     (remove
      nil?
      [(if owner?
         {:title "Delete"
          :icon "trash"
          :on-click #(dispatch [::mi/delete-custom-item item-key])})
       (if owner?
         {:title "Edit"
          :icon "pencil"
          :on-click #(dispatch [::mi/edit-custom-item item])})])
     [:div.p-10.main-text-color
      [item-component item]]]))

(defn base-builder-field [name comp]
  [:div.field.main-text-color.m-t-0
   [:div.personality-label.f-s-18 name]
   comp])

(defn builder-field [el-type name value on-change attrs & [children]]
  (base-builder-field
   name
   [comps/input-field
    el-type
    value
    on-change
    (update
     attrs
     :style
     assoc
     :height
     "40px")]))

(defn select-builder-field [name value on-change children]
  (base-builder-field
   name
   (cond-> [:select.builder-option.builder-option-dropdown
            [:option {:value :none}
             "None"]]
     children (concat children))))

(defn input-builder-field [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn labeled-checkbox [label selected?]
  [:div.flex.align-items-c.pointer.m-b-10
   (comps/checkbox selected? false)
   [:span.m-l-5.f-s-14 label]])

(defn text-field [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   on-change
   {:class-name "input"}])

(defn textarea-field [{:keys [value on-change]}]
  [comps/input-field
   :textarea
   value
   on-change
   {:class-name "input"}])

(defn number-field [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   (fn [v]
     (on-change
      (if (re-matches #"\d+" v) (js/parseInt v))))
   {:class-name "input"
    :type :number}])

(defn attunement-value [attunement key name]
  [:div
   {:on-click #(dispatch [::mi/toggle-attunement-value key])}
   [labeled-checkbox name ((set attunement) key)]])

(defn attunement-selector [attunement]
  (base-builder-field
   [:div
    [:div.flex.align-items-c.m-b-10
     {:on-click #(dispatch [::mi/toggle-attunement])}
     (comps/checkbox attunement false)
     [:span.f-s-24.f-w-b.m-l-5 "Attunement"]]
    (if attunement
      [:div
       [labeled-checkbox "Any" (= #{:any} (set attunement))]
       [:div.flex.flex-wrap
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Class"]
          [:div
           (doall
            (map
             (fn [{:keys [::template/key ::template/name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (cons
              {::template/key :spellcaster
               ::template/name "Spellcaster"}
              t/base-class-options)))])]
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Alignment"]
          [:div
           [:div.m-b-5]
           (doall
            (map
             (fn [{:keys [key name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (concat
              [{:name "Good"
                :key :good}
               {:name "Evil"
                :key :evil}]
              opt/alignments)))])]]])]))

(defn base-armor-selector []
  (let [mobile? @(subscribe [:mobile?])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Armor"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Armor Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [:key :name]}]
             ^{:key key}
             [:div
              {:on-click #(dispatch [::mi/toggle-subtype key])}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "All"
              :key :all}]
            (map
             (fn [type]
               {:name (str "All " (clojure.core/name type))
                :key type})
             armor/armor-types)
            armor/armor)))])]]]))

(defn base-weapon-selector []
  (let [mobile? @(subscribe [:mobile?])
        other? @(subscribe [::mi/has-subtype? :other])
        versatile? @(subscribe [::mi/item-versatile?])
        melee-ranged @(subscribe [::mi/item-melee-ranged])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Weapon"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Weapon Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [key name]}]
             ^{:key key}
             [:div
              {:on-click #(dispatch [::mi/toggle-subtype key])}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "Custom" :key :other}
             {:name "All" :key :all}
             {:name "All Swords" :key :sword}
             {:name "All Axes" :key :axe}]
            weapon/weapons)))])]]
     (if other?
       [:div.main-text-color.m-b-10.m-t-10
        [:span.f-s-18.f-w-b "Base Weapon Details"]
        [:div.flex.flex-wrap.m-t-10
         [:div
          {:on-click #(dispatch [::mi/toggle-item-finesse?])}
          [labeled-checkbox "Finesse?" @(subscribe [::mi/item-finesse?])]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-versatile?])}
          [labeled-checkbox "Versatile?" versatile?]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-reach?])}
          [labeled-checkbox "Reach?" @(subscribe [::mi/item-reach?])]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-two-handed?])}
          [labeled-checkbox "Two-Handed?" @(subscribe [::mi/item-two-handed?])]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-thrown?])}
          [labeled-checkbox "Thrown?" @(subscribe [::mi/item-thrown?])]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-heavy?])}
          [labeled-checkbox "Heavy?" @(subscribe [::mi/item-heavy?])]]
         [:div.m-l-10
          {:on-click #(dispatch [::mi/toggle-item-ammunition?])}
          [labeled-checkbox "Ammunition?" @(subscribe [::mi/item-ammunition?])]]]
        [:div.flex.flex-wrap
         [:div.m-t-10
          [:div.f-w-b.m-b-5 "Damage Die Number"]
          [dropdown
           {:items (map
                    (fn [v]
                      {:value v
                       :title v})
                    (range 1 10))
            :value @(subscribe [::mi/item-damage-die-count])
            :on-change #(dispatch [::mi/set-item-damage-die-count (js/parseInt %)])}]]
         [:div.m-l-10.m-t-10
          [:div.f-w-b.m-b-5 "Damage Die"]
          [dropdown
           {:items (map
                    (fn [v]
                      {:value v
                       :title (str "d" v)})
                    [4 6 8 10 12 20 100])
            :value @(subscribe [::mi/item-damage-die])
            :on-change #(dispatch [::mi/set-item-damage-die (js/parseInt %)])}]]
         (if versatile?
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Versatile Damage Die Number"]
            [dropdown
             {:items (map
                      (fn [v]
                        {:value v
                         :title v})
                      (range 1 10))
              :value @(subscribe [::mi/item-versatile-damage-die-count])
              :on-change #(dispatch [::mi/set-item-versatile-damage-die-count (js/parseInt %)])}]])
         (if versatile?
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Versatile Damage Die"]
            [dropdown
             {:items (map
                      (fn [v]
                        {:value v
                         :title (str "d" v)})
                      [4 6 8 10 12 20 100])
              :value @(subscribe [::mi/item-versatile-damage-die])
              :on-change #(dispatch [::mi/set-item-versatile-damage-die (js/parseInt %)])}]])
         [:div.m-l-10.m-t-10
          [:div.f-w-b.m-b-5 "Simple / Martial?"]
          [dropdown
           {:items [{:value :simple
                     :title "Simple"}
                    {:value :martial
                     :title "Martial"}]
            :value @(subscribe [::mi/item-weapon-type])
            :on-change #(dispatch [::mi/set-item-weapon-type %])}]]
         [:div.m-l-10.m-t-10
          [:div.f-w-b.m-b-5 "Melee / Ranged?"]
          [dropdown
           {:items [{:value :melee
                     :title "Melee"}
                    {:value :ranged
                     :title "Ranged"}]
            :value melee-ranged
            :on-change #(dispatch [::mi/set-item-melee-ranged %])}]]
         (if (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Min"]
            [number-field
             {:value @(subscribe [::mi/item-range-min])
              :on-change #(dispatch [::mi/set-item-range-min %])}]])
         (if (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Max"]
            [number-field
             {:value @(subscribe [::mi/item-range-max])
              :on-change #(dispatch [::mi/set-item-range-max %])}]])
         [:div.m-l-10.m-t-10
          [:div.f-w-b.m-b-5 "Damage Type"]
          [dropdown
           {:items (map
                    (fn [type]
                      {:value type
                       :title (name type)})
                    damage-types/damage-types)
            :value @(subscribe [::mi/item-damage-type])
            :on-change #(dispatch [::mi/set-item-damage-type %])}]]]])]))

(defn item-ability-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Ability Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (s/upper-case (name ability-kw))]
         [:div
          [dropdown
           {:value @(subscribe [::mi/ability-mod-type ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-type ability-kw %])
            :items [{:value :becomes-at-least
                     :title "Becomes At Least"}
                    {:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/ability-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-saving-throw-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Saving Throw Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (str (s/upper-case (name ability-kw)) " Save")]
         [:div
          [dropdown
           {:value :increases-by
            :items [{:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/save-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-save-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-speed-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Speed Bonus"]
   [:div
    (doall
     (map
      (fn [type-kw]
        (let [speed-mod-type @(subscribe [::mi/speed-mod-type type-kw])]
          ^{:key type-kw}
          [:div.flex.align-items-c
           [:div.w-100 (s/capitalize (common/kw-to-name type-kw))]
           [:div
            [dropdown
             {:value speed-mod-type
              :on-change #(dispatch [::mi/set-speed-mod-type type-kw %])
              :items (let [items [{:value :becomes-at-least
                                   :title "Becomes At Least"}
                                  {:value :increases-by
                                   :title "Increases By"}]]
                       (if (= :speed type-kw)
                         items
                         (conj items
                               {:value :equals-walking-speed
                                :title "Equals Walking Speed"})))}]]
           (if (not= :equals-walking-speed speed-mod-type)
             [:div.w-60.m-l-5
              [number-field
               {:value @(subscribe [::mi/speed-mod-value type-kw])
                :on-change #(dispatch [::mi/set-speed-mod-value type-kw %])}]])]))
      [:speed :flying-speed :swimming-speed :climbing-speed]))]))

(defn item-modifier-toggles [title item-kws toggle-event has-sub]
  (base-builder-field
   [:div.f-w-b.m-b-5 title]
   [:div
    (doall
     (map
      (fn [type-kw]
        ^{:key type-kw}
        [:div
         {:on-click #(dispatch [toggle-event type-kw])}
         [labeled-checkbox (s/capitalize (name type-kw)) @(subscribe [has-sub type-kw])]])
      item-kws))]))

(defn item-damage-resistances []
  [item-modifier-toggles
   "Damage Resistances"
   opt/damage-types
   ::mi/toggle-damage-resistance
   ::mi/has-damage-resistance?])

(defn item-damage-vulnerabilities []
  [item-modifier-toggles
   "Damage Vulnerabilities"
   opt/damage-types
   ::mi/toggle-damage-vulnerability
   ::mi/has-damage-vulnerability?])

(defn item-damage-immunities []
  [item-modifier-toggles
   "Damage Immunities"
   opt/damage-types
   ::mi/toggle-damage-immunity
   ::mi/has-damage-immunity?])

(defn item-condition-immunities []
  [item-modifier-toggles
   "Condition Immunities"
   (keys opt/conditions-map)
   ::mi/toggle-condition-immunity
   ::mi/has-condition-immunity?])

(defn item-bonuses [{:keys [::mi/magical-damage-bonus
                            ::mi/magical-attack-bonus
                            ::mi/magical-ac-bonus
                            ::mi/type] :as item}]
  [:div.m-b-20
   [:div.m-b-10
    [:span.f-s-24.f-w-b "Item Properties"]]
   [:div.flex.m-b-20
    (if (= type :weapon)
      [:div
       [:div.f-w-b.m-b-5 "Magical Damage Bonus"]
       [number-field
        {:value magical-damage-bonus
         :on-change #(dispatch [::mi/set-item-damage-bonus %])}]])
    (if (= type :weapon)
      [:div.m-l-20.m-r-20
       [:div.f-w-b.m-b-5 "Magical Attack Bonus"]
       [number-field
        {:value magical-attack-bonus
         :on-change #(dispatch [::mi/set-item-attack-bonus %])}]])
    [:div
     [:div.f-w-b.m-b-5 "Magical AC Bonus"]
     [number-field
      {:value magical-ac-bonus
       :on-change #(dispatch [::mi/set-item-ac-bonus %])}]]]
   [:div.flex.flex-wrap.m-b-20
    [:div.flex-grow-1
     [item-ability-bonuses]]
    [:div.flex-grow-1
     [item-saving-throw-bonuses]]
    [:div.flex-grow-1
     [item-speed-bonuses]]]
   [:div.flex.flex-wrap
    [:div.flex-grow-1
     [item-damage-resistances]]
    [:div.flex-grow-1
     [item-damage-vulnerabilities]]
    [:div.flex-grow-1
     [item-damage-immunities]]
    [:div.flex-grow-1
     [item-condition-immunities]]]])

(defn dropdown [{:keys [items value on-change]}]
  [:select.builder-option.builder-option-dropdown.m-t-0
   {:value (or value "")
    :on-change #(on-change (event-value %))}
   (doall
    (map
     (fn [{:keys [value title]}]
       ^{:key value}
       [:option.builder-dropdown-item
        {:value value}
        title])
     items))])

(defn item-builder []
  (let [{:keys [::mi/name ::mi/type ::mi/rarity ::mi/description ::mi/attunement] :as item}
        @(subscribe [::mi/builder-item])
        item-types @(subscribe [::mi/item-types])
        item-rarities @(subscribe [::mi/rarities])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20
       [input-builder-field
        "Item Name"
        name
        #(dispatch [::mi/set-item-name %])
        {:class-name "input"}]]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Type"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [type-kw]
                     {:value type-kw
                      :title (common/kw-to-name type-kw)})
                   item-types)
           :value type
           :on-change #(dispatch [::mi/set-item-type %])}]])]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Rarity"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [rarity]
                     {:value rarity
                      :title (clojure.core/name rarity)})
                   item-rarities)
           :value rarity
           :on-change #(dispatch [::mi/set-item-rarity %])}]]
        [:select.builder-option.builder-option-dropdown])]]
     [:div.m-b-40 (base-builder-field "Description" [textarea-field
                                                     {:value description
                                                      :on-change #(dispatch [::mi/set-item-description %])}])]
     (if (= :armor type)
       [:div.m-b-40 [base-armor-selector]])
     (if (= :weapon type)
       [:div.m-b-40 [base-weapon-selector]])
     [:div.m-b-40
      [attunement-selector attunement]]
     [item-bonuses item]]))

(defn item-builder-page []
  [content-page
   "Item Builder Page"
   [{:title "New Item"
     :icon "plus"
     :on-click #(dispatch [::mi/reset-item])}
    {:title "Save"
     :icon "save"
     :on-click #(dispatch [::mi/save-item])}]
   [item-builder]])


(defn character-list []
  (let [characters @(subscribe [::char/characters])
        expanded-characters @(subscribe [:expanded-characters])
        device-type @(subscribe [:device-type])
        username @(subscribe [:username])
        selected-ids @(subscribe [::char/selected])
        has-selected? @(subscribe [::char/has-selected?])]
    [content-page
     "Characters"
     [{:title "New"
       :icon "plus"
       :on-click #(dispatch [:new-character])}
      {:title "Make Party"
       :icon "users"
       :class-name (if (not has-selected?) "opacity-5 cursor-disabled")
       :on-click #(if has-selected? (dispatch [::party/make-party selected-ids]))}]
     [:div.p-5
      [:div
       (let [grouped-characters (group-by ::se/owner characters)
             user-characters (find grouped-characters username)
             other-characters (sort-by key (dissoc grouped-characters username))
             sorted-groups (if user-characters
                             (cons user-characters other-characters)
                             other-characters)]
         (doall
          (map
           (fn [[owner owner-characters]]
             ^{:key owner}
             [:div.m-b-40
              [:div.m-b-10.main-text-color.f-w-b.f-s-16
               [other-user-component owner "f-s-24 m-l-10 m-r-20 i" true]]
              [:div.item-list
               (doall
                (map
                 (fn [{:keys [:db/id ::se/owner] :as summary}]
                   (let [expanded? (get expanded-characters id)
                         char-page-path (routes/path-for routes/dnd-e5-char-page-route :id id)
                         char-page-route (routes/match-route char-page-path)]
                     ^{:key id}
                     [:div.main-text-color.item-list-item
                      [:div
                       [:div.flex.justify-cont-s-b.align-items-c.pointer
                        {:on-click #(dispatch [:toggle-character-expanded id])}
                        [:div.m-l-10.flex.align-items-c
                         [:div.p-5
                          {:on-click (fn [e]
                                       (dispatch [::char/toggle-selected id])
                                       (.stopPropagation e))}
                          (comps/checkbox (get selected-ids id) false)]
                         [:div.f-s-24.f-w-600
                          [:div.list-character-summary
                           [character-summary-2 summary true owner false]]]]
                        [:div.orange.pointer.m-r-10
                         (if (not= device-type :mobile) [:span.underline (if expanded?
                                                                           "collapse"
                                                                           "open")])
                         [:i.fa.m-l-5
                          {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                       (if expanded?
                         [:div
                          {:style character-display-style}
                          [:div.flex.justify-cont-end.uppercase.align-items-c
                           [share-link id]
                           [:div.m-r-5 [character-page-fb-button id]]
                           (if (= username owner)
                             [:button.form-button
                              {:on-click #(dispatch [:edit-character @(subscribe [::char/character id])])}
                              "edit"])
                           [:button.form-button.m-l-5
                            {:on-click #(let [route char-page-route]
                                          (dispatch [:route route]))}
                            "view"]
                           [:button.form-button.m-l-5
                            {:on-click (export-pdf
                                        @(subscribe [::char/built-character id])
                                        {:print-character-sheet? true
                                         :print-spell-cards? true
                                         :print-prepared-spells? false})}
                            "print"]
                           (if (= username owner)
                             [:button.form-button.m-l-5
                              {:on-click #(dispatch [::char/show-delete-confirmation id])}
                              "delete"])]
                          (if @(subscribe [::char/delete-confirmation-shown? id])
                            [:div.p-20.flex.justify-cont-end
                             [:div
                              [:div.m-b-10 "Are you sure you want to delete this character?"]
                              [:div.flex
                               [:button.form-button
                                {:on-click #(dispatch [::char/hide-delete-confirmation id])}
                                 "cancel"]
                               [:span.link-button
                                {:on-click #(dispatch [:delete-character id])}
                                 "delete"]]]])
                          [character-display id false (if (= :mobile device-type) 1 2)]])]]))
                 (sort-by ::char/character-name owner-characters)))]])
           sorted-groups)))]]]))

(def party-name-editor-style
  {:width "200px"
   :height "42px"})

(defn parties []
  (let [editing-parties (r/atom {})
        expanded-characters (r/atom {})]
    (fn []
      (let [parties @(subscribe [::party/parties])
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         "Parties"
         []
         [:div.p-5
          [:div
           (doall
            (map
             (fn [{:keys [:db/id ::party/name] characters ::party/character-ids}]
               (let [editing? (get @editing-parties id)
                     character-ids (into #{} (map :db/id) characters)]
                 ^{:key id}
                 [:div.m-b-40
                  [:div.m-b-10.main-text-color.f-w-b.f-s-16
                   [:div.flex.align-items-c
                    [:i.fa.fa-users.m-l-10]
                    (if editing?
                      [:div.flex.align-items-c.flex-wrap
                       [:input.input.m-l-10
                        {:value (or (@editing-parties id) name)
                         :style party-name-editor-style
                         :on-change #(swap! editing-parties assoc id (event-value %))}]
                       [:div.m-l-10
                        {:style {:width "200px"}}
                        [comps/selection-adder
                         (sequence
                          (comp
                           (remove
                            (fn [{:keys [:db/id]}]
                              (character-ids id)))
                           (map
                            (fn [{:keys [:db/id ::char/character-name]}]
                              {:name character-name
                               :key id})))
                          @(subscribe [::char/characters]))
                         (fn [e]
                           (let [selected-id (js/parseInt (.. e -target -value))]
                             (dispatch [::party/add-character id selected-id])))]]
                       [:div.m-t-5
                        [:button.form-button.m-l-10
                         {:on-click #(do (dispatch [::party/rename-party id (@editing-parties id)])
                                         (swap! editing-parties assoc id nil))}
                         "save"]
                        [:button.form-button.m-l-10
                         {:on-click #(dispatch [::party/delete-party id])}
                         "delete"]
                        [:button.form-button.m-l-10
                         {:on-click #(swap! editing-parties assoc id nil)}
                         "cancel"]]]
                      [:div.flex.align-items-c
                       [:span.m-l-5 name]
                       [:i.fa.fa-pencil.m-l-10.opacity-5.hover-opacity-full.pointer
                        {:on-click #(swap! editing-parties assoc id name)}]])]]
                  [:div.item-list
                   (doall
                    (map
                     (fn [{:keys [::se/owner] :as summary}]
                       (let [character-id (:db/id summary)
                             expanded? (get-in @expanded-characters [id character-id])
                             char-page-path (routes/path-for routes/dnd-e5-char-page-route :id character-id)
                             char-page-route (routes/match-route char-page-path)]
                         ^{:key character-id}
                         [:div.main-text-color.item-list-item
                          [:div.pointer
                           [:div.flex.justify-cont-s-b.align-items-c
                            {:on-click #(swap! expanded-characters update-in [id character-id] not)}
                            [:div.m-l-10.flex.align-items-c
                             [:div.f-s-24.f-w-600
                              [:div.list-character-summary
                               [character-summary-2 summary true owner true false]]]]
                            [:div.orange.pointer.m-r-10
                             (if (not= device-type :mobile) [:span.underline (if expanded?
                                                                               "collapse"
                                                                               "open")])
                             [:i.fa.m-l-5
                              {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                           (if expanded?
                             [:div
                              {:style character-display-style}
                              [:div.flex.justify-cont-end.uppercase.align-items-c
                               (if (= username owner)
                                 [:button.form-button
                                  {:on-click #(dispatch [:edit-character @(subscribe [::char/character character-id])])}
                                  "edit"])
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [:route char-page-route])}
                                "view"]
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [::party/remove-character id character-id])}
                                "remove from party"]]
                              [character-display character-id false (if (= :mobile device-type) 1 2)]])]]))
                     characters))]]))
             parties))]]]))))

(defn monster-list-item [{:keys [name size type subtypes alignment key] :as monster}]
  (let [expanded? @(subscribe [:monster-expanded? name])
        monster-page-path (routes/path-for routes/dnd-e5-monster-page-route :key key)
        monster-page-route (routes/match-route monster-page-path)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click #(dispatch [:toggle-monster-expanded name])}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-b-20.p-t-20
         [monster-summary name size type subtypes alignment]]]
       [:div.orange.pointer.m-r-10
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click #(dispatch [:route monster-page-route])}
           "view"]]
         [monster-component monster]])]]))

(defn monster-list-items [expanded-monsters device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name] :as monster}]
       ^{:key name}
       [monster-list-item monster])
     @(subscribe [::char/filtered-monsters])))])

(defn monster-list []
  (let [filters-expanded? (r/atom false)]
    (fn []
      (let [expanded-monsters @(subscribe [:expanded-monsters])
            device-type @(subscribe [:device-type])]
        [content-page
         "Monsters"
         []
         [:div.p-l-5.p-r-5.p-b-10
          [:div.p-b-10.p-l-10.p-r-10
           [:div.posn-rel
            [:input.input.f-s-24.p-l-20.w-100-p
             {:style {:height "60px"}
              :value @(subscribe [::char/monster-text-filter])
              :on-change #(dispatch [::char/filter-monsters (event-value %)])}]
            [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
             {:style close-icon-style
              :on-click #(dispatch [::char/filter-monsters ""])}]]]
          [:div
           [:div.flex.justify-cont-end.m-b-10
            [:div.orange.pointer.m-r-10
             {:on-click #(swap! filters-expanded? not)}
             (if (not= device-type :mobile)
               [:span.underline (if @filters-expanded?
                                  "hide"
                                  "filters")])
             [:i.fa.m-l-5
              {:class-name (if @filters-expanded? "fa-caret-up" "fa-caret-down")}]]]
           (if @filters-expanded?
             [:div.flex.flex-wrap
              [:div.main-text-color.p-20
               [:div.f-s-16.f-w-b "Size"]
               [:div
                (doall
                 (map
                  (fn [size]
                    ^{:key size}
                    [:div.p-5.pointer
                     {:on-click #(dispatch [::char/toggle-monster-filter-hidden :size size])}
                     (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :size size])) false)
                     (common/kw-to-name size)])
                  @(subscribe [::char/monster-sizes])))]]
              [:div.main-text-color.p-20
               [:div.f-s-16.f-w-b "Type"]
               [:div
                (doall
                 (map
                  (fn [type]
                    ^{:key type}
                    [:div.p-5.pointer
                     {:on-click #(dispatch [::char/toggle-monster-filter-hidden :type type])}
                     (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :type type])) false)
                     (common/kw-to-name type)])
                  @(subscribe [::char/monster-types])))]]
              (let [subtypes @(subscribe [::char/monster-subtypes])]
                [:div.main-text-color.p-20
                 [:div.f-s-16.f-w-b "Subtype"]
                 [:div
                  (doall
                   (map
                    (fn [subtype]
                      ^{:key subtype}
                      [:div.p-5.pointer
                       {:on-click #(dispatch [::char/toggle-monster-filter-hidden :subtype subtype])}
                       (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :subtype subtype])) false)
                       (common/kw-to-name subtype)])
                    subtypes))]])])]
          [monster-list-items expanded-monsters device-type]]]))))

(defn spell-list-item [{:keys [name level school key] :as spell}]
  (let [expanded? @(subscribe [:spell-expanded? name])
        spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
        spell-page-route (routes/match-route spell-page-path)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click #(dispatch [:toggle-spell-expanded name])}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20
         [spell-summary name level school true 12]]]
       [:div.orange.pointer.m-r-10
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click #(dispatch [:route spell-page-route])}
           "view"]]
         [spell-component spell true]])]]))

(defn spell-list-items [expanded-spells device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name level school key] :as spell}]
       ^{:key name}
       [spell-list-item spell])
     @(subscribe [::char/filtered-spells])))])

(defn spell-list []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Spells"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p
         {:style {:height "60px"}
          :value @(subscribe [::char/spell-text-filter])
          :on-change #(dispatch [::char/filter-spells (event-value %)])}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click #(dispatch [::char/filter-spells ""])}]]]
      [spell-list-items expanded-spells device-type]]]))

(defn item-list-item [{:keys [key name ::mi/owner :db/id] :as item} expanded?]
  (let [expanded-key (or name (::mi/name item))
        expanded? @(subscribe [:item-expanded? expanded-key])
        username @(subscribe [:username])
        item-page-path (routes/path-for routes/dnd-e5-item-page-route :key (or id key))
        item-page-route (routes/match-route item-page-path)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click #(dispatch [:toggle-item-expanded expanded-key])}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20
         [item-summary item]]]
       [:div.orange.pointer.m-r-10
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click #(dispatch [:route item-page-route])}
           "view"]
          (if (= username owner)
            [:button.form-button.m-l-5
             {:on-click #(dispatch [::mi/edit-custom-item @(subscribe [::mi/custom-item id])])}
             "edit"])]
         [item-component item]])]]))

(defn item-list-items []
  [:div.item-list
   (doall
    (map
     (fn [{:keys [:db/id key] :as item}]
       ^{:key (or key id)}
       [item-list-item item])
     @(subscribe [::char/filtered-items])))])

(defn item-list []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Items"
     [[:button.form-button
       {:on-click #(dispatch [::mi/new-item])}
       [:div.flex.align-items-c.white
        [svg-icon "beer-stein" 18 ""]
        [:span.m-l-5 "New Item"]]]]
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p
         {:style {:height "60px"}
          :value @(subscribe [::char/item-text-filter])
          :on-change #(dispatch [::char/filter-items (event-value %)])}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click #(dispatch [::char/filter-items ""])}]]]
      [item-list-items]]]))
