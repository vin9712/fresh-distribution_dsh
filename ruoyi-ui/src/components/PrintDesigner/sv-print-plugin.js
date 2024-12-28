// sv-print-plugin.js

function initHideWatermark({ Config, OptionItems, hinnn, hiprint, name }) {
  console.log('initHideWatermark Config', Config, 'OptionItems', OptionItems, 'hinnn', hinnn, 'hiprint', hiprint, 'name', name);
  // 删除预设水印的 setWatermark 插件
  Config.plugins = Config.plugins.filter(plugin => plugin.name !== "setWatermark");

  // 隐藏无用的组件
  const existStyle = document.getElementById("hide-sv-print-style")
  if (existStyle) {
    existStyle.remove();
  }
  const hideStyle = document.createElement("style");
  hideStyle.id = "hide-sv-print-style";
  hideStyle.innerHTML = `
        #dragBox-rotateTools,
        #SVPrint .svp-footer {
          display: none !important;
        }
      `;
  document.head.appendChild(hideStyle);
}

const plugin = function (config) {
  let configs = config || {};
  return {
    name: "sv-print-plugin",
    description: "功能：隐藏水印和无用组件",
    hooks: [
      {
        hook: "init",
        priority: 1,
        run(opts) {
          initHideWatermark(Object.assign({}, opts, configs));
        }
      }],
    leastHiprintVersion: "0.1.0"
  };
};

// 导出插件以便在 Vue 中使用
export default plugin;