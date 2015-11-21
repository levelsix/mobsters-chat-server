{:config
 ;MySQL config
 {;DynamoDB config
  ; For DynamoDB Local, just put some random string
  ; For production, put your IAM keys here
  :dynamodb             {:access-key "AKIAIPMBU57QNKCO3RJQ"
                         :secret-key "VUJ6psXzXgacpecC718B3UUEm8AYwj6uqWykKGY8"
                         ;;; Local or a different AWS Region (Ref. http://goo.gl/YmV80o), etc.:
                         ;; :endpoint "http://localhost:8000"                   ; For Local
                         ;; :endpoint "http://dynamodb.eu-west-1.amazonaws.com" ; For EU West 1 AWS region
                         :endpoint "http://localhost:8000"}
  ;Misc config
  :error-log            "/var/log/lvl6chat/error.log"
  :table-prefix         "chatdev"}}