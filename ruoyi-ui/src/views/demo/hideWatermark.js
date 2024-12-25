// scalePlugin.js

// 因为是在 Vue 项目中使用，这里假设我们不需要 import/export 语句，除非你在使用 ES 模块。
// 如果你需要导出此插件以供其他地方使用，你可以选择保留 export 语句。

function hideWatermarkBeforePrint({ template, info, opts }) {
    console.log('template', template, 'info', info, 'opts', opts);
    return undefined;
}

const plugin = function (config) {
    let configs = config || {};
    return {
        name: "hideWatermark",
        description: "打印前隐藏水印",
        hooks: [{
            hook: "beforePrint",
            priority: 1,
            run(opts) {
                hideWatermarkBeforePrint(Object.assign({}, opts, configs));
            }
        }],
        leastHiprintVersion: "0.1.0"
    };
};

// 导出插件以便在 Vue 中使用
export default plugin;