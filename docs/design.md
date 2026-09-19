# 近友 NearCircle

原生 Java Android 熟人社交 App，配套 Java 17 / Spring Boot 服务端。用途是可运行的移动软件作品和可验证的工程实践，不宣称商用即时通信规模。用户已选择 Java Android 与好友、单聊、朋友圈，授权实现及上传 GitHub。

## 交付与边界
- 独立仓库、Android APK、服务端、可重复测试、启动脚本、真实界面截图、中文架构与面试讲解。
- 注册登录、搜索用户、申请好友、接收/通过/拒绝申请、好友列表；一对一文本聊天、朋友圈文字和单图、点赞、评论、删除自己的动态；个人资料修改。
- 不实现群聊、音视频通话、支付、通讯录读取、手机号短信、推荐算法。图片通过系统选择器选取，不申请全部文件访问。
- API 使用 bearer opaque token，存储 token hash、密码 PBKDF2；Android token 使用 Android Keystore 加密保存。测试账号只由显式 demo 初始化创建。
- 单机持久化数据库；好友是双向关系，申请的收件人才能同意。非好友不能读写私聊、查看朋友圈、图片、点赞评论。图片鉴权获取，不公开静态目录。
- 服务端消息先持久化后广播，客户端按 server id 拉取补齐。消息 client_id 幂等：同发送者同 client_id 同内容返回既有记录，不同内容返回 409。
- Room 保存账户隔离消息与发件箱。WorkManager 唯一任务驱动重试，联网约束；退出登录取消旧账户任务、清除会话，发送始终使用创建任务时的账号上下文。Activity 重建不丢失已入队消息。
- WebSocket 只作为前台刷新信号，不承诺后台永久连接；重连/恢复前台通过 HTTP 补齐。强行停止应用后的系统限制明确写入文档。
- 朋友圈游标分页；头像为姓名字母/汉字+固定色，不上传头像以缩小媒体权限面。
- UI：奶白背景、墨绿重点色、衬底分区、轻量线性图标；四个标签：消息/好友/朋友圈/我；空状态、加载、错误、离线状态明确，真实双账号可演示。

## 固定 API 契约
Base `/api`, JSON snake_case, id 是 long，时间戳是 Unix epoch 毫秒。对象 User={id,username,display_name,bio}。错误 {message}。不允许客户端指定 sender_id、owner_id。

- POST `/auth/register` {username,password,display_name} -> {token,user}; POST `/auth/login` {username,password} -> 同上; POST `/auth/logout` -> {}。
- GET `/me` -> User; PATCH `/me` {display_name,bio} -> User。
- GET `/users?q=` -> {items:[User]}，q 至少 2 字符，结果最多 20，排除本人。
- GET `/friends` -> {items:[User]}; POST `/friend-requests` {user_id} -> {}; GET `/friend-requests` -> {items:[{id,from_user,to_user,status,created_at}]}，包括本人所有收发的 pending 申请；POST `/friend-requests/{id}/accept` / `reject` -> {}。
- GET `/conversations` -> {items:[{peer:User,last_message:{...Message}|null,unread_count}]}，按最后消息倒序。
- GET `/messages/{peerId}?after_id=0&limit=100` -> {items:[Message],has_more:boolean}; 返回双方聊天按 id 正序，limit 1..100，after_id exclusive。
- POST `/messages/{peerId}` {client_id,body} -> Message；Message={id,client_id,sender_id,recipient_id,body,created_at}，body 1..2000 字符。
- POST `/messages/{peerId}/read` {through_id} -> {}。必须只能标记此会话收到的消息。
- POST `/media` multipart `file` -> {id}; PNG/JPEG/WebP <=5MiB，验证并重编码去除元数据，最长边<=4096，总像素<=16M。
- GET `/media/{id}` -> image，只有所有者或能看见引用该图朋友圈的好友可访问。
- POST `/posts` {body,media_id?:string} -> Post; GET `/posts?before_id=...&limit=20` -> {items:[Post],next_cursor:long|null}; DELETE `/posts/{id}` -> {}。
- PUT `/posts/{id}/like` {liked:boolean} -> Post，幂等；POST `/posts/{id}/comments` {body} -> Post。
- Post={id,author:User,body,media_id:null|string,created_at,liked:boolean,like_count,comments:[{id,author:User,body,created_at}]}。
- POST `/ws-ticket` -> {ticket}，单次 30s ticket；WS `/ws?ticket=...`，服务端文本 JSON {type:"refresh"}，客户端收到后重新拉取，不把广播当可靠消息源。
- GET `/health` -> {status:"ok"}。
- demo profile 显式开启：账号 lin / zhou / xu，密码 NearCircle2026!，含已建立好友、聊天、动态；默认 profile 不初始化这些账号。

## 验收
服务端真实 HTTP/数据库测试：身份验证、朋友圈越权、好友申请越权、消息幂等冲突与重试、游标分页、不合法输入、媒体越权、注销失效。Android JVM 测试关键协议/排序/重试规则；编译、lint；有条件时模拟器双账号端到端、屏幕旋转、离线补发与截图。未实测项目须明确标注，不用单元测试代替真机声明。
