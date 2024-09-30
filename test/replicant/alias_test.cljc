(ns replicant.alias-test
  (:require [replicant.alias :as sut]
            [clojure.test :refer [deftest is testing]]))

(def dictionaries
  {:en {:click "Click"
        :no-click "Don't click"
        :thing-1 "Item #1"
        :thing-2 "Item #2"}})

(defn i18n [locale _ [k]]
  (get-in dictionaries [locale k]))

(defn button [attrs [text-k]]
  [:button.btn (select-keys attrs [:class])
   [:ui/i18n text-k]])

(defn panel [{:keys [buttons class]} & _]
  [:div.panel (cond-> {}
                class (assoc :class class))
   (for [button buttons]
     [(if (:primary? button)
        :ui/button.btn-primary
        :ui/button)
      (:text-k button)])])

(defn html-list [_ items]
  [:ul.list items])

(def aliases
  {:ui/button #'button
   :ui/panel #'panel
   :ui/i18n #(apply i18n :en %&)
   :ui/list #'html-list})

(deftest expand-1-test
  (testing "Expands first level of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:ui/button.btn-primary :click]
            [:ui/button :no-click]])))

  (testing "Expands two levels of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand-1 {:aliases aliases})
               (sut/expand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} [:ui/i18n :click]]
            [:button {:class #{"btn"}} [:ui/i18n :no-click]]])))

  (testing "Passes children to alias fn as a seq and flattens expanded hiccup"
    (is (= (-> [:ui/list
                [:li "One thing"]
                [:li "Another thing"]]
               (sut/expand-1 {:aliases aliases}))
           [:ul {:class #{"list"}}
            [:li "One thing"]
            [:li "Another thing"]])))

  (testing "Expands only known aliases"
    (is (= (-> [:ui/list
                [:li [:ui/i18n :thing-1]]
                [:li [:ui/i18n :thing-2]]]
               (sut/expand-1 {:aliases (select-keys aliases [:ui/i18n])}))
           [:ui/list {}
            [:li "Item #1"]
            [:li "Item #2"]])))

  (testing "Fails missing aliases when explicitly told to"
    (is (= (-> [:ui/list
                [:li [:ui/i18n :thing-1]]
                [:li [:ui/i18n :thing-2]]]
               (sut/expand-1 {:aliases (select-keys aliases [:ui/i18n])
                              :ignore-missing-alias? false}))
           [:div {:data-replicant-error "Undefined alias :ui/list"}
            [:li "Item #1"]
            [:li "Item #2"]]))))

(deftest expand-test
  (testing "Expands all aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} "Click"]
            [:button {:class #{"btn"}} "Don't click"]]))))
