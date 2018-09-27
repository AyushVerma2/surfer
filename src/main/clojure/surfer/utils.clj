(ns surfer.utils
  (:import 
    [java.security MessageDigest]
    [java.nio.charset StandardCharsets]))

(defn to-bytes ^bytes [data]
  (cond
    (string? data) (.getBytes ^String data StandardCharsets/UTF_8)))

(defn hex [i]
  (.charAt "0123456789abcdef" (mod i 16)))

(defn byte-to-hex [^long b]
  (str (hex (unsigned-bit-shift-right b 4)) (hex b) ))

(defn hex-string [^bytes data]
  (apply str (map byte-to-hex data)))

(defn sha256 [msg]
  (let [data (to-bytes msg)
        md (MessageDigest/getInstance "SHA-256")]
    (.digest md data)))
