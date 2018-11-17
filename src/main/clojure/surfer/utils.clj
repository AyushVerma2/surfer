(ns surfer.utils
  (:import 
    [java.security MessageDigest]
    [java.util UUID]
    [java.nio.charset StandardCharsets]
    [org.bouncycastle.crypto.digests KeccakDigest] ))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def array-of-bytes-type (Class/forName "[B")) 

(def EMPTY-BYTES (byte-array 0))

(defn to-bytes ^bytes [data]
  (cond
    (string? data) (.getBytes ^String data StandardCharsets/UTF_8)
    (= (class data) array-of-bytes-type) data
    (nil? data) EMPTY-BYTES
    :else (throw (IllegalArgumentException. (str "Can't convert to bytes: " (class data))))))

(defn valid-id-char?
  "Returns true if c is a valid lowercase hex character."
  ([^long c]
    (or  
      (and (<= 48 c) (<= c 57)) ;; digit
      (and (<= 97 c) (<= c 102));; lowercase a-f
      )))

(defn valid-asset-id? 
  "Returns true iff given a valid asset id string."
  ([^String s]
    (and 
      (string? s)
      (== 64 (count s))
      (loop [i (int 0)]
        (if (< i 53) 
          (let [c (int (.charAt s i))
                c (long c)]
            (if (valid-id-char? c)
              (recur (inc i))
              false))
          true)))))

(defn hex [i]
  (.charAt "0123456789abcdef" (mod i 16)))

(defn byte-to-hex [^long b]
  (str (hex (unsigned-bit-shift-right b 4)) (hex b) ))

(defn hex-string [^bytes data]
  (apply str (map byte-to-hex data)))

(defn sha256 
  "Compute sha256 hash of a message.

   Returns an array of 32 bytes."
  [msg]
  (let [data (to-bytes msg)
        md (MessageDigest/getInstance "SHA-256")]
    (.digest md data)))

(defn keccak256
  "Compute keccak256 hash of a message.

   Returns an array of 32 bytes."
  [msg]
  (let [data (to-bytes msg)
        md (KeccakDigest. 256)
        result (byte-array 32)]
    (.update md data (int 0) (int (count data)))
    (.doFinal md result 0)
    result))

(defn new-random-id 
  "Creates a new random hex ID of the given length. 

   Default length is 64."
  ([] (new-random-id 64))
  ([^long length]
    (let [uuid (UUID/randomUUID)
         hash (keccak256 (str uuid))
         hex (hex-string hash)
         bs (* 64 (quot (dec length) 64))
         tail (subs hex 0 (- length bs))]
      (if (> bs 0)
        (str (new-random-id bs) tail)
        tail))))
