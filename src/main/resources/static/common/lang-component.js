// 通用 - 语言切换+拖拽功能封装
(function() {
    // 1. 状态初始化
    let currentLang = localStorage.getItem('appLang') || 'cn'; // 默认中文

    // 统一转成标准格式：cn -> zh (为了符合 HTML 标准，也可以继续用 cn，只要和 CSS 对应即可)
    // 这里为了配合 CSS 里的 html[lang="en"]，我们约定：中文='zh'，英文='en'
    if (currentLang === 'cn') currentLang = 'zh';

    const langSelector = document.getElementById('langSelector');
    const langTrigger = document.getElementById('langTrigger');

    // 判断是否为移动端
    const isMobile = window.innerWidth <= 576 || 'ontouchstart' in window;

    // 2. 初始化/应用语言的核心函数 (修改版)
    function applyLangState(lang) {
        // === 🔥 核心修改：只修改 HTML 顶层属性，剩下的交给 CSS ===
        document.documentElement.setAttribute('lang', lang);

        // 切换输入框占位符（这个还是需要 JS 做，因为 placeholder 不能用 CSS 控制）
        document.querySelectorAll('input[data-placeholder-en]').forEach(input => {
            input.placeholder = lang === 'zh'
                ? (input.getAttribute('data-placeholder-cn') || input.defaultValue || "请输入") // 兜底
                : input.getAttribute('data-placeholder-en');
        });

        // 触发全局事件（通知 index.html 这种需要重算时间文字的页面）
        window.dispatchEvent(new Event('languageChange'));
    }

    // 3. 暴露给外部调用的切换函数
    window.switchLang = function(lang) {
        // 统一参数格式
        if (lang === 'cn') lang = 'zh';

        currentLang = lang;
        // 存入本地，下次打开还是这个语言
        // 注意：为了兼容旧代码可能存的 'cn'，这里存进去的还是转换后的 'zh' 或 'en'
        localStorage.setItem('appLang', lang);

        applyLangState(lang);

        // 关闭下拉菜单
        if (langSelector) langSelector.classList.remove('active');
    };

    // 兼容旧代码里的 applyLang 调用（防止报错）
    window.applyLang = function() {
        // 空函数，因为 CSS 已经接管了一切
    };

    // 4. PC端交互逻辑 (保持原样，你的代码写的很好)
    function initPcInteraction() {
        if (!langSelector || !langTrigger || isMobile) return;

        // 点击展开
        langTrigger.addEventListener('click', (e) => {
            if (!isDragging) {
                langSelector.classList.toggle('active');
            }
        });

        // 点击外部关闭
        document.addEventListener('click', (e) => {
            if (langSelector && !langSelector.contains(e.target)) {
                langSelector.classList.remove('active');
            }
        });
    }

    // 5. 拖拽功能 (保持原样)
    let isDragging = false;
    function initDragFeature() {
        if (!langSelector || isMobile) return;

        let pressStartTime;
        let startX, startY, offsetX, offsetY;

        langSelector.addEventListener('mousedown', function(e) {
            if (e.target.closest('.lang-option')) return;
            pressStartTime = Date.now();
            const rect = langSelector.getBoundingClientRect();
            startX = e.clientX;
            startY = e.clientY;
            offsetX = startX - rect.left;
            offsetY = startY - rect.top;
        });

        document.addEventListener('mousemove', function(e) {
            if (!pressStartTime) return;
            if (!isDragging) {
                if (Date.now() - pressStartTime > 200) {
                    isDragging = true;
                    langSelector.classList.add('dragging');
                    langSelector.classList.remove('active');
                } else {
                    return;
                }
            }
            updateDragPosition(e);
        });

        function updateDragPosition(e) {
            const windowWidth = window.innerWidth;
            const windowHeight = window.innerHeight;
            const compWidth = langSelector.offsetWidth;
            const compHeight = langSelector.offsetHeight;

            let newLeft = e.clientX - offsetX;
            let newTop = e.clientY - offsetY;

            newLeft = Math.max(10, Math.min(windowWidth - compWidth - 10, newLeft));
            newTop = Math.max(10, Math.min(windowHeight - compHeight - 10, newTop));

            langSelector.style.left = `${newLeft}px`;
            langSelector.style.top = `${newTop}px`;
            langSelector.style.right = 'auto'; // 覆盖 CSS 的 right
            langSelector.style.bottom = 'auto';
        }

        function resetDrag() {
            pressStartTime = null;
            setTimeout(() => isDragging = false, 50); // 防止拖拽结束触发点击
            langSelector.classList.remove('dragging');
        }
        document.addEventListener('mouseup', resetDrag);
        document.addEventListener('mouseleave', resetDrag);
    }

    // 6. 启动
    function init() {
        applyLangState(currentLang);
        initPcInteraction();
        initDragFeature();
    }

    window.addEventListener('load', init);
})();