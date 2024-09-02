(ns replicant.string-test
  (:require [replicant.string :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest string-render-test
  (testing "Renders string"
    (is (= (sut/render "Hello world") "Hello world")))

  (testing "Renders simple element"
    (is (= (sut/render [:h1 "Hello world"]) "<h1>Hello world</h1>")))

  (testing "Renders id from hiccup tag"
    (is (= (sut/render [:h1#heading "Hello world"])
           "<h1 id=\"heading\">Hello world</h1>")))

  (testing "Renders id"
    (is (= (sut/render [:h1 {:id "headz"} "Hello world"])
           "<h1 id=\"headz\">Hello world</h1>")))

  (testing "Renders classes"
    (is (= (sut/render [:h1.text-sm {:class ["border-4"]} "Hello"])
           "<h1 class=\"border-4 text-sm\">Hello</h1>")))

  (testing "Renders styles from maps"
    (is (= (sut/render [:h1 {:style {:border "1px solid red"
                                     :color "red"}} "Red"])
           "<h1 style=\"border: 1px solid red; color: red;\">Red</h1>")))

  (testing "Renders styles from strings"
    (is (= (sut/render [:h1 {:style "border: 1px solid red; color: yellow"} "Red"])
           "<h1 style=\"border: 1px solid red; color: yellow;\">Red</h1>")))

  (testing "Pixelizes relevant styles"
    (is (= (sut/render [:h1 {:style {:border-width 2}} "Box"])
           "<h1 style=\"border-width: 2px;\">Box</h1>")))

  (testing "Renders number attributes as stringified numbers"
    (is (= (sut/render [:img {:height 10}])
           "<img height=\"10\">")))
  
  (testing "Renders arbitrary attributes"
    (is (= (sut/render [:h1 {:title "Color"} "Red"])
           "<h1 title=\"Color\">Red</h1>")))

  (testing "Ignores event handlers"
    (is (= (sut/render [:h1 {:on {:click [:doit]}} "Hello"])
           "<h1>Hello</h1>")))

  (testing "Ignores replicant props"
    (is (= (sut/render [:h1 {:replicant/key "key"} "Hello"])
           "<h1>Hello</h1>")))

  (testing "Renders nested hiccup"
    (is (= (sut/render
            [:div
             [:h1.heading "Hello world"]
             [:p "Some text"]])
           "<div><h1 class=\"heading\">Hello world</h1><p>Some text</p></div>")))

  (testing "Renders formatted markup"
    (is (= (sut/render
            [:div
             [:h1.heading "Hello world"]
             [:p "Some text"]]
            {:indent 2})
           (str "<div>\n"
                "  <h1 class=\"heading\">\n"
                "    Hello world\n"
                "  </h1>\n"
                "  <p>\n"
                "    Some text\n"
                "  </p>\n"
                "</div>\n"))))

  (testing "Renders SVG node"
    (is (= (sut/render
            [:svg {:viewBox "0 0 100 100"}
             [:g [:use {:xlink:href "#icon"}]]])
           (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                "<g>"
                "<use xlink:href=\"#icon\"></use>"
                "</g>"
                "</svg>"))))
  
  (testing "Only generates one xmlns attribute for SVG node"
    (is (= (sut/render
            [:svg {:xmlns "http://www.w3.org/2000/svg"}])
           "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")))

  (testing "Ignores nil styles"
    (is (= (sut/render
            [:div {:style {:border nil
                           :background "yellow"}}
             "Text"])
           "<div style=\"background: yellow;\">Text</div>")))

  (testing "Ignores nil classes"
    (is (= (sut/render
            [:div {:class [nil "lol"]}
             "Text"])
           "<div class=\"lol\">Text</div>")))

  (testing "Ignores nil ids"
    (is (= (sut/render
            [:div {:id nil}
             "Text"])
           "<div>Text</div>")))

  (testing "Renders empty string attributes"
    (is (= (sut/render
            [:div {:enabled ""}
             "Text"])
           "<div enabled>Text</div>")))

  (testing "Renders boolean attributes"
    (is (= (sut/render
            [:div {:enabled true}
             "Text"])
           "<div enabled>Text</div>")))

  (testing "Skips nil children"
    (is (= (sut/render
            [:div nil [:div "Ok"]])
           "<div><div>Ok</div></div>")))

  (testing "Escapes HTML"
    (is (= (sut/render
            [:div "<script>alert(\"boom\")</script>"])
           "<div>&lt;script&gt;alert(&quot;boom&quot;)&lt;/script&gt;</div>")))

  (testing "Passes through raw strings"
    (is (= (sut/render
            [:replicant/raw-string "<script>alert(\"boom\")</script>"])
           "<script>alert(\"boom\")</script>"))))

(deftest escape-html-test
  (is (= (sut/escape-html "<script>alert(\"boom\")</script>")
         "&lt;script&gt;alert(&quot;boom&quot;)&lt;/script&gt;")))
