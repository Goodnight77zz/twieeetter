import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'app_lang.dart';
import 'tweet_detail_page.dart';
import 'api_config.dart';
import 'api_helpers.dart';

class MyContentPage extends StatefulWidget {
  const MyContentPage({super.key});

  @override
  State<MyContentPage> createState() => _MyContentPageState();
}

class _MyContentPageState extends State<MyContentPage> {
  final Color mainBlue = const Color(0xFF00A1D6);

  List<dynamic> allTweets = []; // 所有稿件
  List<dynamic> filteredTweets = []; // 筛选后的稿件
  bool isLoading = true;
  String selectedTag = ""; // 当前选中的标签 (空代表全部)

  // 标签列表 (需与 publish_page 保持一致)
  final List<String> _legacyTags = [
    "AI",
    "生物医学",
    "计算机",
    "物理",
    "化学",
    "深度学习",
    "数据分析",
    "未分类",
  ];

  List<String> tags = const [];

  @override
  void initState() {
    super.initState();
    _loadMyTweets();
  }

  Future<void> _loadMyTweets() async {
    final prefs = await SharedPreferences.getInstance();
    int userId = prefs.getInt('userId') ?? 0;

    try {
      var res = await createSessionDio().get('$baseUrl/tweets/user/$userId');
      if (mounted) {
        setState(() {
          allTweets = asList(res.data);
          tags = _extractTags(allTweets);
          if (tags.isEmpty) {
            tags = List<String>.from(_legacyTags);
          }
          _applyFilter(); // 加载完自动筛选一次
          isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) setState(() => isLoading = false);
    }
  }

  // 🔍 本地筛选逻辑
  void _applyFilter() {
    if (selectedTag.isEmpty || selectedTag == "All") {
      filteredTweets = List.from(allTweets);
    } else {
      filteredTweets = allTweets.where((t) {
        final item = asMap(t);
        String tTags = item['tags']?.toString() ?? "";
        return tTags.contains(selectedTag);
      }).toList();
    }
  }

  List<String> _extractTags(List<dynamic> tweets) {
    final tagSet = <String>{};
    for (final item in tweets) {
      final rawTags = asMap(item)['tags']?.toString() ?? '';
      if (rawTags.trim().isEmpty) {
        continue;
      }
      for (final part in rawTags.split(',')) {
        final tag = part.trim();
        if (tag.isNotEmpty) {
          tagSet.add(tag);
        }
      }
    }
    final values = tagSet.toList()..sort();
    return values;
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();

    return Scaffold(
      appBar: AppBar(title: Text(lang.t('my_content_title'))),
      body: Column(
        children: [
          // 1. 顶部筛选栏
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: Colors.white,
            child: Row(
              children: [
                Icon(Icons.filter_list, color: mainBlue),
                const SizedBox(width: 10),
                Text(
                  lang.t('filter_tag'),
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      value: selectedTag.isEmpty ? null : selectedTag,
                      hint: Text(lang.t('all_tags')),
                      isExpanded: true,
                      items: [
                        DropdownMenuItem(
                          value: "",
                          child: Text(lang.t('all_tags')),
                        ),
                        ...tags.map(
                          (t) => DropdownMenuItem(value: t, child: Text(t)),
                        ),
                      ],
                      onChanged: (val) {
                        setState(() {
                          selectedTag = val ?? "";
                          _applyFilter();
                        });
                      },
                    ),
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),

          // 2. 列表区域
          Expanded(
            child: isLoading
                ? Center(child: CircularProgressIndicator(color: mainBlue))
                : filteredTweets.isEmpty
                ? Center(
                    child: Text(
                      lang.t('no_content'),
                      style: const TextStyle(color: Colors.grey),
                    ),
                  )
                : ListView.builder(
                    itemCount: filteredTweets.length,
                    padding: const EdgeInsets.all(12),
                    itemBuilder: (context, index) {
                      final item = filteredTweets[index];
                      return _buildSimpleCard(item, lang);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  // 简化的稿件卡片
  Widget _buildSimpleCard(dynamic tweet, LangProvider lang) {
    final item = asMap(tweet);
    final String timeStr = (item['createTime'] ?? '').toString().split('T')[0];
    final String title = (item['title'] ?? item['content'] ?? '').toString();
    final String tagsStr = item['tags']?.toString() ?? "";

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: Colors.grey.shade200),
      ),
      child: InkWell(
        onTap: () {
          // 跳转详情
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (c) => TweetDetailPage(tweetId: asInt(item['id'])),
            ),
          );
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.blue.shade50,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      Icons.description,
                      color: Colors.blue,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          timeStr,
                          style: const TextStyle(
                            color: Colors.grey,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Colors.grey,
                  ),
                ],
              ),
              if (tagsStr.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: Wrap(
                    spacing: 8,
                    children: tagsStr
                        .split(',')
                        .map(
                          (t) => Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFF0F0F0),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              "#$t",
                              style: const TextStyle(
                                fontSize: 11,
                                color: Colors.black54,
                              ),
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
