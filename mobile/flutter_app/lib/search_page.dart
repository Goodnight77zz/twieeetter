import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_config.dart';
import 'app_lang.dart';
import 'tweet_adapter.dart';
import 'tweet_detail_page.dart';
import 'api_helpers.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage>
    with SingleTickerProviderStateMixin {
  final Color mainBlue = const Color(0xFF00A1D6);
  late TabController _tabController;
  final TextEditingController _searchCtrl = TextEditingController();

  List<Map<String, dynamic>> tweetResults = [];
  List<Map<String, dynamic>> userResults = [];
  List<Map<String, dynamic>> mySubscriptions = [];
  Map<String, dynamic>? interestProfile;
  Map<String, dynamic>? aiIntent;
  Set<int> myFollowingIds = {};

  bool isSearching = false;
  bool hasSearched = false;
  bool isAiParsing = false;
  int currentUserId = 0;
  String currentSort = 'latest';
  String aiStatus = '';
  String? selectedResearchArea;
  String? selectedContentType;
  String? selectedPublicationType;
  String? selectedStatus;
  String? selectedLanguage;

  static const List<String> _researchAreas = [
    'AI',
    'Biomedical',
    'Computer Science',
    'Physics',
    'Chemistry',
    'Deep Learning',
    'Data Analysis',
  ];

  static const Map<String, String> _contentTypes = {
    'paper': 'Paper',
    'dataset': 'Dataset',
    'project': 'Project',
    'report': 'Report',
    'code': 'Code',
  };

  static const Map<String, String> _publicationTypes = {
    'journal': 'Journal',
    'conference': 'Conference',
    'preprint': 'Preprint',
    'thesis': 'Thesis',
    'internal': 'Internal',
  };

  static const Map<String, String> _statuses = {
    'draft': 'Draft',
    'ongoing': 'Ongoing',
    'submitted': 'Submitted',
    'published': 'Published',
  };

  static const Map<String, String> _languages = {
    'English': 'English',
    'Chinese': 'Chinese',
    'Bilingual': 'Bilingual',
  };

  static const Map<String, String> _sorts = {
    'latest': 'Latest',
    'hot': 'Hot',
    'topRated': 'Top Rated',
    'mostDiscussed': 'Discussed',
    'recommended': 'Recommended',
  };

  bool get _isTweetTab => _tabController.index == 0;

  bool get _hasFilters =>
      selectedResearchArea != null ||
      selectedContentType != null ||
      selectedPublicationType != null ||
      selectedStatus != null ||
      selectedLanguage != null;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _tabController.addListener(_onTabChanged);
    _initUser();
  }

  @override
  void dispose() {
    _tabController.removeListener(_onTabChanged);
    _tabController.dispose();
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onTabChanged() {
    if (!_tabController.indexIsChanging && mounted) {
      setState(() {});
    }
  }

  Future<void> _initUser() async {
    final prefs = await SharedPreferences.getInstance();
    currentUserId = prefs.getInt('userId') ?? 0;
    await _fetchMyFollowing();
  }

  Future<void> _fetchMyFollowing() async {
    if (currentUserId == 0) return;
    try {
      final res = await createSessionDio().get(
        '$baseUrl/users/$currentUserId/following',
      );
      if (!mounted) return;
      setState(() {
        myFollowingIds = asList(
          res.data,
        ).map((u) => asInt(asMap(u)['id'])).where((id) => id > 0).toSet();
      });
    } catch (_) {}
  }

  Future<void> _loadDiscoveryContext() async {
    if (currentUserId == 0) {
      if (!mounted) return;
      setState(() {
        mySubscriptions = [];
        interestProfile = null;
      });
      return;
    }
    try {
      final results = await Future.wait<Object>([
        createSessionDio().get(
          '$baseUrl/subscriptions/my',
          queryParameters: {'userId': currentUserId},
        ),
        createSessionDio().get(
          '$baseUrl/users/$currentUserId/interest-profile',
        ),
      ]);
      if (!mounted) return;
      setState(() {
        final subscriptionsRes = results[0] as Response<dynamic>;
        final interestProfileRes = results[1] as Response<dynamic>;
        mySubscriptions = asMapList(subscriptionsRes.data);
        interestProfile = asMap(interestProfileRes.data);
      });
    } catch (_) {}
  }

  Future<void> _doSearch() async {
    final keyword = _searchCtrl.text.trim();
    if (!_isTweetTab && keyword.isEmpty) return;
    if (_isTweetTab &&
        keyword.isEmpty &&
        !_hasFilters &&
        currentSort == 'latest') {
      return;
    }

    setState(() {
      isSearching = true;
      hasSearched = true;
    });
    FocusScope.of(context).unfocus();

    try {
      final results = await Future.wait<Object>([
        _searchTweets(keyword),
        createSessionDio().get(
          '$baseUrl/users/search',
          queryParameters: {'keyword': keyword},
        ),
      ]);
      if (!mounted) return;
      setState(() {
        final userSearchRes = results[1] as Response<dynamic>;
        tweetResults = results[0] as List<Map<String, dynamic>>;
        userResults = asMapList(userSearchRes.data);
        isSearching = false;
      });
      await _fetchMyFollowing();
    } catch (_) {
      if (mounted) setState(() => isSearching = false);
    }
  }

  Future<List<Map<String, dynamic>>> _searchTweets(String keyword) async {
    if (currentSort == 'recommended') {
      await _loadDiscoveryContext();
    }
    final res = await createSessionDio().get(
      '$baseUrl/tweets/search',
      queryParameters: {
        'keyword': keyword,
        if (selectedResearchArea != null) 'researchArea': selectedResearchArea,
        if (selectedContentType != null) 'contentType': selectedContentType,
        if (selectedPublicationType != null)
          'publicationType': selectedPublicationType,
        if (selectedStatus != null) 'status': selectedStatus,
        if (selectedLanguage != null) 'language': selectedLanguage,
        'sort': currentSort == 'recommended' ? 'latest' : currentSort,
        'limit': 50,
      },
    );
    final tweets = asList(
      res.data,
    ).map(normalizeTweetItem).whereType<Map<String, dynamic>>().toList();
    if (currentSort == 'recommended') {
      tweets.sort(
        (a, b) => _scorePost(b, keyword).compareTo(_scorePost(a, keyword)),
      );
    }
    return tweets;
  }

  Future<void> _runAiSearch() async {
    final query = _searchCtrl.text.trim();
    if (query.isEmpty) return;
    setState(() {
      isAiParsing = true;
      aiStatus = 'AI is analyzing your search intent...';
    });
    try {
      final res = await createSessionDio().post(
        '$baseUrl/ai/search-helper',
        data: FormData.fromMap({
          'query': query,
          'lang': context.read<LangProvider>().currentLang == 'zh'
              ? 'zh'
              : 'en',
        }),
      );
      final parsed = _parseAiIntent((res.data['result'] ?? '').toString());
      if (parsed == null) throw const FormatException('parse failed');
      if (!mounted) return;
      setState(() {
        aiIntent = parsed;
        _applyAiIntent(parsed);
        aiStatus =
            'AI filters parsed successfully. Search executed automatically.';
      });
      await _doSearch();
    } catch (_) {
      if (mounted) {
        setState(
          () => aiStatus = 'AI search parsing failed. Please try again later.',
        );
      }
    } finally {
      if (mounted) setState(() => isAiParsing = false);
    }
  }

  Map<String, dynamic>? _parseAiIntent(String text) {
    if (text.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(text);
      if (decoded is Map) return Map<String, dynamic>.from(decoded);
    } catch (_) {}
    final start = text.indexOf('{');
    final end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      try {
        final decoded = jsonDecode(text.substring(start, end + 1));
        if (decoded is Map) return Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    return null;
  }

  void _applyAiIntent(Map<String, dynamic> intent) {
    selectedResearchArea = _clean(intent['researchArea'] ?? intent['area']);
    selectedContentType = _pickMatch(
      _contentTypes.keys,
      intent['contentType'] ?? intent['type'],
    );
    selectedPublicationType = _pickMatch(
      _publicationTypes.keys,
      intent['publicationType'],
    );
    selectedStatus = _pickMatch(_statuses.keys, intent['status']);
    selectedLanguage = _normalizeLanguage(intent['language']);
    currentSort = _normalizeSort(intent['sort']);
    final keyword = _clean(intent['keyword'] ?? intent['query']);
    if (keyword != null) _searchCtrl.text = keyword;
  }

  String _normalizeSort(dynamic value) {
    final text = _clean(value)?.toLowerCase() ?? '';
    if (text == 'hot') return 'hot';
    if (text == 'toprated' || text == 'top_rated') return 'topRated';
    if (text == 'mostdiscussed' || text == 'most_discussed') {
      return 'mostDiscussed';
    }
    if (text == 'recommended' || text == 'recommend') return 'recommended';
    return 'latest';
  }

  String? _normalizeLanguage(dynamic value) {
    final text = _clean(value)?.toLowerCase();
    if (text == null) return null;
    if (text == 'zh' || text == 'cn' || text == 'chinese') return 'Chinese';
    if (text == 'en' || text == 'english') return 'English';
    if (text == 'bilingual' || text == 'dual') return 'Bilingual';
    return null;
  }

  String? _pickMatch(Iterable<String> source, dynamic value) {
    final text = _clean(value)?.toLowerCase();
    if (text == null) return null;
    for (final item in source) {
      if (item.toLowerCase() == text) return item;
    }
    return null;
  }

  String? _clean(dynamic value) {
    final text = value?.toString().trim();
    if (text == null || text.isEmpty || text.toLowerCase() == 'null') {
      return null;
    }
    return text;
  }

  int _scorePost(Map<String, dynamic> post, String keyword) {
    var score = 0;
    final area = (post['researchArea'] ?? '').toString().trim().toLowerCase();
    final title = (post['title'] ?? '').toString().toLowerCase();
    final content = (post['content'] ?? '').toString().toLowerCase();
    final tags = (post['tags'] ?? '').toString().toLowerCase();
    final author = asMap(post['author']);
    final interests = asList(interestProfile?['interestAreas'])
        .map((item) => asMap(item))
        .map(
          (item) => (item['name'] ?? item['label'] ?? '')
              .toString()
              .trim()
              .toLowerCase(),
        )
        .where((value) => value.isNotEmpty)
        .toList();
    final subs = mySubscriptions
        .map(
          (item) =>
              (item['researchArea'] ?? item['targetValue'] ?? '').toString(),
        )
        .map((value) => value.trim().toLowerCase())
        .where((value) => value.isNotEmpty)
        .toSet();

    if (area.isNotEmpty && subs.contains(area)) score += 36;
    if (area.isNotEmpty && interests.contains(area)) score += 28;
    for (final item in interests.take(4)) {
      if (title.contains(item) ||
          content.contains(item) ||
          tags.contains(item)) {
        score += 10;
      }
    }
    if (keyword.isNotEmpty && title.contains(keyword.toLowerCase())) {
      score += 12;
    }
    score += (asDouble(post['avgScore']) * 2).round();
    score += (asDouble(post['hotScore']) / 10).round();
    score += (asDouble(post['commentCount']) / 3).round();
    if (asInt(author['id']) == currentUserId) {
      score -= 15;
    }
    return score;
  }

  Future<void> _toggleFollow(int targetId) async {
    if (currentUserId == 0) return;
    final isFollowing = myFollowingIds.contains(targetId);
    setState(() {
      isFollowing
          ? myFollowingIds.remove(targetId)
          : myFollowingIds.add(targetId);
    });
    try {
      await createSessionDio().post(
        '$baseUrl/users/$currentUserId/${isFollowing ? 'unfollow' : 'follow'}',
        queryParameters: {'targetUserId': targetId},
      );
    } catch (_) {
      if (!mounted) return;
      setState(() {
        isFollowing
            ? myFollowingIds.add(targetId)
            : myFollowingIds.remove(targetId);
      });
    }
  }

  Future<void> _subscribeArea(String area) async {
    if (currentUserId == 0 || area.trim().isEmpty) return;
    try {
      await createSessionDio().post(
        '$baseUrl/subscriptions',
        queryParameters: {'userId': currentUserId, 'researchArea': area.trim()},
      );
      await _loadDiscoveryContext();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Subscribed to $area')));
    } catch (_) {}
  }

  bool _isSubscribed(String area) {
    final normalized = area.trim().toLowerCase();
    return mySubscriptions.any((item) {
      final value = (item['researchArea'] ?? item['targetValue'] ?? '')
          .toString()
          .trim()
          .toLowerCase();
      return value == normalized;
    });
  }

  void _resetFilters() {
    setState(() {
      selectedResearchArea = null;
      selectedContentType = null;
      selectedPublicationType = null;
      selectedStatus = null;
      selectedLanguage = null;
      currentSort = 'latest';
      aiIntent = null;
      aiStatus = '';
    });
    if (hasSearched && _isTweetTab) {
      _doSearch();
    }
  }

  @override
  Widget build(BuildContext context) {
    final lang = context.watch<LangProvider>();
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        titleSpacing: 0,
        backgroundColor: Colors.white,
        elevation: 0.5,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.grey),
          onPressed: () => Navigator.pop(context),
        ),
        title: Container(
          height: 40,
          margin: const EdgeInsets.only(right: 8),
          decoration: BoxDecoration(
            color: const Color(0xFFF1F5F9),
            borderRadius: BorderRadius.circular(20),
          ),
          child: TextField(
            controller: _searchCtrl,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _doSearch(),
            autofocus: true,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              hintText: lang.t('search_page_hint'),
              prefixIcon: const Icon(
                Icons.search,
                size: 20,
                color: Colors.grey,
              ),
              suffixIcon: _searchCtrl.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(
                        Icons.clear,
                        size: 18,
                        color: Colors.grey,
                      ),
                      onPressed: () => setState(() => _searchCtrl.clear()),
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 0),
            ),
          ),
        ),
        actions: [
          IconButton(
            tooltip: lang.t('search_btn'),
            onPressed: _doSearch,
            icon: Icon(Icons.search, color: mainBlue),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(40),
          child: TabBar(
            controller: _tabController,
            isScrollable: true,
            labelColor: mainBlue,
            unselectedLabelColor: Colors.grey,
            indicatorColor: mainBlue,
            indicatorSize: TabBarIndicatorSize.label,
            tabs: [
              Tab(text: lang.t('tab_search_tweets')),
              Tab(text: lang.t('tab_search_users')),
            ],
          ),
        ),
      ),
      body: Column(
        children: [
          if (_isTweetTab) _buildDiscoveryPanel(),
          Expanded(
            child: isSearching
                ? Center(child: CircularProgressIndicator(color: mainBlue))
                : !hasSearched
                ? _buildIntro()
                : TabBarView(
                    controller: _tabController,
                    children: [_buildTweetList(lang), _buildUserList(lang)],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildDiscoveryPanel() {
    final interestAreas = asList(interestProfile?['interestAreas']);
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFFF8FBFF), Color(0xFFEEF6FF)],
              ),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: const Color(0xFFD8E8FB)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Discovery Tools',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          SizedBox(height: 6),
                          Text(
                            'AI intent parsing, structured filters, and recommended ranking are now available on mobile.',
                            style: TextStyle(
                              fontSize: 12,
                              height: 1.5,
                              color: Color(0xFF475569),
                            ),
                          ),
                        ],
                      ),
                    ),
                    ElevatedButton.icon(
                      onPressed: isAiParsing ? null : _runAiSearch,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2563EB),
                        foregroundColor: Colors.white,
                      ),
                      icon: const Icon(Icons.auto_awesome, size: 16),
                      label: Text(isAiParsing ? 'Thinking...' : 'AI Search'),
                    ),
                  ],
                ),
                if (aiStatus.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Text(
                    aiStatus,
                    style: TextStyle(
                      color: aiStatus.contains('failed')
                          ? const Color(0xFFB42318)
                          : const Color(0xFF245D85),
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _dropdown(
                  'Research Area',
                  selectedResearchArea,
                  _researchAreas
                      .map(
                        (e) =>
                            DropdownMenuItem<String?>(value: e, child: Text(e)),
                      )
                      .toList(),
                  (v) {
                    setState(() => selectedResearchArea = v);
                    if (hasSearched) _doSearch();
                  },
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _dropdown(
                  'Content Type',
                  selectedContentType,
                  _contentTypes.entries
                      .map(
                        (e) => DropdownMenuItem<String?>(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  (v) {
                    setState(() => selectedContentType = v);
                    if (hasSearched) _doSearch();
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _dropdown(
                  'Publication',
                  selectedPublicationType,
                  _publicationTypes.entries
                      .map(
                        (e) => DropdownMenuItem<String?>(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  (v) {
                    setState(() => selectedPublicationType = v);
                    if (hasSearched) _doSearch();
                  },
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _dropdown(
                  'Status',
                  selectedStatus,
                  _statuses.entries
                      .map(
                        (e) => DropdownMenuItem<String?>(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  (v) {
                    setState(() => selectedStatus = v);
                    if (hasSearched) _doSearch();
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _dropdown(
                  'Language',
                  selectedLanguage,
                  _languages.entries
                      .map(
                        (e) => DropdownMenuItem<String?>(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  (v) {
                    setState(() => selectedLanguage = v);
                    if (hasSearched) _doSearch();
                  },
                ),
              ),
              TextButton.icon(
                onPressed: _resetFilters,
                icon: const Icon(Icons.restart_alt),
                label: const Text('Reset'),
              ),
            ],
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 38,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: _sorts.entries
                  .map(
                    (e) => Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(e.value),
                        selected: currentSort == e.key,
                        onSelected: (_) {
                          setState(() => currentSort = e.key);
                          if (hasSearched) _doSearch();
                        },
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
          if (currentSort == 'recommended') ...[
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FBFF),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: const Color(0xFFD8E8FB)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    interestProfile?['dominantInterest']
                                ?.toString()
                                .isNotEmpty ==
                            true
                        ? 'Recommended for ${interestProfile!['dominantInterest']}'
                        : 'Recommended ranking',
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF1E3A8A),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    interestProfile?['profileSummary']?.toString() ??
                        'This ranking uses interest profile, subscriptions, tags, and current signals.',
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF475569),
                      height: 1.5,
                    ),
                  ),
                  if (interestAreas.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: interestAreas.take(4).map((item) {
                        final map = asMap(item);
                        final label = (map['name'] ?? map['label'] ?? '-')
                            .toString();
                        return Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: const Color(0xFFCFE1FF)),
                          ),
                          child: Text(
                            label,
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF2563EB),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildIntro() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.travel_explore, size: 64, color: Colors.grey.shade300),
            const SizedBox(height: 12),
            Text(
              _isTweetTab ? 'Start discovering research' : 'Search for users',
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            Text(
              _isTweetTab
                  ? 'Search titles, keywords, DOI, or let AI convert natural language into filters.'
                  : 'Search researchers and browse follow suggestions.',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFF64748B), height: 1.6),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTweetList(LangProvider lang) {
    if (tweetResults.isEmpty) return _buildEmpty(lang);
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: tweetResults.length,
      itemBuilder: (_, index) {
        final item = tweetResults[index];
        final author = asMap(item['author']);
        final title = (item['title'] ?? '').toString().trim();
        final tags = (item['tags'] ?? '').toString();
        final area = (item['researchArea'] ?? '').toString().trim();
        final chips = [
          (item['researchArea'] ?? '').toString(),
          (item['publicationType'] ?? '').toString(),
          (item['status'] ?? '').toString(),
          (item['language'] ?? '').toString(),
        ].where((v) => v.trim().isNotEmpty).toList();
        final subscribed = area.isNotEmpty && _isSubscribed(area);
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: Colors.grey.shade200),
          ),
          elevation: 0,
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => TweetDetailPage(tweetId: asInt(item['id'])),
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 22,
                        backgroundImage: NetworkImage(buildAvatarUrl(author)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              (author['nickname'] ??
                                      author['username'] ??
                                      'User')
                                  .toString(),
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              (item['createTime'] ?? '').toString().split(
                                'T',
                              )[0],
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey.shade500,
                              ),
                            ),
                          ],
                        ),
                      ),
                      if ((item['avgScore'] ?? 0) > 0)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFFFF7D6),
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: const Color(0xFFFDE68A)),
                          ),
                          child: Text(
                            'Score ${asDouble(item['avgScore']).toStringAsFixed(1)}',
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                    ],
                  ),
                  if (title.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                  if (chips.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: chips.map((chip) {
                        return Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFF8FAFC),
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(color: const Color(0xFFE2E8F0)),
                          ),
                          child: Text(
                            chip,
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFF475569),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                  const SizedBox(height: 10),
                  Text(
                    (item['content'] ?? '').toString(),
                    maxLines: 4,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 14, height: 1.5),
                  ),
                  if (tags.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: tags
                          .split(',')
                          .map((tag) => tag.trim())
                          .where((tag) => tag.isNotEmpty)
                          .map((tag) {
                            return Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              decoration: BoxDecoration(
                                color: const Color(0xFFE8F5FE),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Text(
                                '#${lang.tTag(tag)}',
                                style: TextStyle(fontSize: 11, color: mainBlue),
                              ),
                            );
                          })
                          .toList(),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _meta(
                        Icons.thumb_up_alt_outlined,
                        '${item['likeCount'] ?? 0}',
                      ),
                      _meta(
                        Icons.mode_comment_outlined,
                        '${item['commentCount'] ?? 0}',
                      ),
                      _meta(
                        Icons.download_outlined,
                        '${item['downloadCount'] ?? 0}',
                      ),
                      _meta(
                        Icons.local_fire_department_outlined,
                        '${(item['hotScore'] ?? 0).round()}',
                      ),
                      _meta(
                        Icons.bookmark_outline,
                        '${item['bookmarkCount'] ?? 0}',
                      ),
                    ],
                  ),
                  if (area.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF8FBFF),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: const Color(0xFFD8E8FB)),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              subscribed
                                  ? 'You already follow "$area".'
                                  : 'Follow "$area" to receive updates.',
                              style: const TextStyle(
                                fontSize: 12,
                                height: 1.45,
                                color: Color(0xFF475569),
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          subscribed
                              ? Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 8,
                                  ),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFE8F5FE),
                                    borderRadius: BorderRadius.circular(999),
                                  ),
                                  child: Text(
                                    'Subscribed',
                                    style: TextStyle(
                                      color: mainBlue,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                )
                              : ElevatedButton(
                                  onPressed: () => _subscribeArea(area),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: mainBlue,
                                    foregroundColor: Colors.white,
                                  ),
                                  child: const Text('Subscribe'),
                                ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildUserList(LangProvider lang) {
    final users = userResults
        .where((user) => user['id'] != currentUserId)
        .toList();
    if (users.isEmpty) return _buildEmpty(lang);
    return ListView.builder(
      itemCount: users.length,
      itemBuilder: (_, index) {
        final user = users[index];
        final isFollowing = myFollowingIds.contains(user['id']);
        return ListTile(
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 8,
          ),
          leading: CircleAvatar(
            radius: 24,
            backgroundImage: NetworkImage(buildAvatarUrl(user)),
          ),
          title: Text(
            (user['nickname'] ?? user['username']).toString(),
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          subtitle: Text(
            'ID: ${user['username']}',
            style: const TextStyle(fontSize: 12),
          ),
          trailing: SizedBox(
            height: 32,
            width: 88,
            child: ElevatedButton(
              onPressed: () => _toggleFollow(asInt(user['id'])),
              style: ElevatedButton.styleFrom(
                backgroundColor: isFollowing ? Colors.grey.shade200 : mainBlue,
                foregroundColor: isFollowing ? Colors.grey : Colors.white,
                elevation: 0,
                padding: EdgeInsets.zero,
              ),
              child: Text(
                isFollowing ? lang.t('btn_followed') : lang.t('btn_follow'),
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildEmpty(LangProvider lang) {
    return Center(
      child: Text(
        lang.t('search_empty'),
        style: TextStyle(color: Colors.grey.shade400),
      ),
    );
  }

  Widget _dropdown(
    String label,
    String? value,
    List<DropdownMenuItem<String?>> items,
    ValueChanged<String?> onChanged,
  ) {
    return DropdownButtonFormField<String?>(
      initialValue: value,
      isExpanded: true,
      items: [
        const DropdownMenuItem<String?>(value: null, child: Text('All')),
        ...items,
      ],
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: label,
        filled: true,
        fillColor: const Color(0xFFF8FAFC),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }

  Widget _meta(IconData icon, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: const Color(0xFFE4E7EC)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.grey.shade600),
          const SizedBox(width: 6),
          Text(
            value,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}
