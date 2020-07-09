
(require [hy.extra.anaphoric [*]])
(import [bs4 [BeautifulSoup]])
(import requests)

(defn get-info
  [code]
  (-> (requests.get f"http://fund.eastmoney.com/{code}.html")
      (doto
        (-> (. encoding)
            (setv "gbk2312")))
      (. text)
      (BeautifulSoup "lxml")))

(setv r (get-info "470009"))
(setv li (r.find "li" :id "fundManagerTab"))

(->> (li.table.find-all "tr")
     rest
     (ap-map (->> (zip ["start" "name" "days" "percent"]
                       [(-> (.find it "td" :class_ "td01")
                            (. text))
                        (-> (.find it "td" :class_ "td02")
                            (. a text))
                        (-> (.find it "td" :class_ "td03")
                            (. text))
                        (-> (.find it "td" :class_ "td04")
                            (. text))
                        ])
                  (dict)))
     list
     print)
