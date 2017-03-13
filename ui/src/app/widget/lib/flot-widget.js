/*
 * Copyright © 2016-2017 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import $ from 'jquery';
import tinycolor from 'tinycolor2';
import moment from 'moment';
import 'flot/lib/jquery.colorhelpers';
import 'flot/src/jquery.flot';
import 'flot/src/plugins/jquery.flot.time';
import 'flot/src/plugins/jquery.flot.selection';
import 'flot/src/plugins/jquery.flot.pie';
import 'flot/src/plugins/jquery.flot.crosshair';
import 'flot/src/plugins/jquery.flot.stack';

/* eslint-disable angular/angularelement */
export default class TbFlot {
    constructor(ctx, chartType) {

        this.ctx = ctx;
        this.chartType = chartType || 'line';

        var colors = [];
        for (var i in ctx.data) {
            var series = ctx.data[i];
            colors.push(series.dataKey.color);
            var keySettings = series.dataKey.settings;

            series.lines = {
                fill: keySettings.fillLines === true,
                show: this.chartType === 'line' ? keySettings.showLines !== false : keySettings.showLines === true
            };

            series.points = {
                show: false,
                radius: 8
            };
            if (keySettings.showPoints === true) {
                series.points.show = true;
                series.points.lineWidth = 5;
                series.points.radius = 3;
            }

            var lineColor = tinycolor(series.dataKey.color);
            lineColor.setAlpha(.75);

            series.highlightColor = lineColor.toRgbString();

        }
        ctx.tooltip = $('#flot-series-tooltip');
        if (ctx.tooltip.length === 0) {
            ctx.tooltip = $("<div id=flot-series-tooltip' class='flot-mouse-value'></div>");
            ctx.tooltip.css({
                fontSize: "12px",
                fontFamily: "Roboto",
                fontWeight: "300",
                lineHeight: "18px",
                opacity: "1",
                backgroundColor: "rgba(0,0,0,0.7)",
                color: "#D9DADB",
                position: "absolute",
                display: "none",
                zIndex: "100",
                padding: "4px 10px",
                borderRadius: "4px"
            }).appendTo("body");
        }

        var tbFlot = this;

        function seriesInfoDiv(label, color, value, units, trackDecimals, active, percent) {
            var divElement = $('<div></div>');
            divElement.css({
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
            });
            var lineSpan = $('<span></span>');
            lineSpan.css({
                backgroundColor: color,
                width: "20px",
                height: "3px",
                display: "inline-block",
                verticalAlign: "middle",
                marginRight: "5px"
            });
            divElement.append(lineSpan);
            var labelSpan = $('<span>' + label + ':</span>');
            labelSpan.css({
                marginRight: "10px"
            });
            if (active) {
                labelSpan.css({
                    color: "#FFF",
                    fontWeight: "700"
                });
            }
            divElement.append(labelSpan);
            var valueContent = tbFlot.ctx.utils.formatValue(value, trackDecimals, units);
            if (angular.isNumber(percent)) {
                valueContent += ' (' + Math.round(percent) + ' %)';
            }
            var valueSpan =  $('<span>' + valueContent + '</span>');
            valueSpan.css({
                marginLeft: "auto",
                fontWeight: "700"
            });
            if (active) {
                valueSpan.css({
                    color: "#FFF"
                });
            }
            divElement.append(valueSpan);

            return divElement;
        }

        if (this.chartType === 'pie') {
            ctx.tooltipFormatter = function(item) {
                var divElement = seriesInfoDiv(item.series.dataKey.label, item.series.dataKey.color,
                    item.datapoint[1][0][1], tbFlot.ctx.trackUnits, tbFlot.ctx.trackDecimals, true, item.series.percent);
                return divElement.prop('outerHTML');
            };
        } else {
            ctx.tooltipFormatter = function(hoverInfo, seriesIndex) {
                var content = '';
                var timestamp = parseInt(hoverInfo.time);
                var date = moment(timestamp).format('YYYY-MM-DD HH:mm:ss');
                var dateDiv = $('<div>' + date + '</div>');
                dateDiv.css({
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    padding: "4px",
                    fontWeight: "700"
                });
                content += dateDiv.prop('outerHTML');
                for (var i in hoverInfo.seriesHover) {
                    var seriesHoverInfo = hoverInfo.seriesHover[i];
                    if (tbFlot.ctx.tooltipIndividual && seriesHoverInfo.index !== seriesIndex) {
                        continue;
                    }
                    var divElement = seriesInfoDiv(seriesHoverInfo.label, seriesHoverInfo.color,
                        seriesHoverInfo.value, tbFlot.ctx.trackUnits, tbFlot.ctx.trackDecimals, seriesHoverInfo.index === seriesIndex);
                    content += divElement.prop('outerHTML');
                }
                return content;
            };
        }

        var settings = ctx.settings;
        ctx.trackDecimals = angular.isDefined(settings.decimals) ?
            settings.decimals : ctx.decimals;

        ctx.trackUnits = angular.isDefined(settings.units) ? settings.units : ctx.units;

        ctx.tooltipIndividual = this.chartType === 'pie' || (angular.isDefined(settings.tooltipIndividual) ? settings.tooltipIndividual : false);
        ctx.tooltipCumulative = angular.isDefined(settings.tooltipCumulative) ? settings.tooltipCumulative : false;

        var font = {
            color: settings.fontColor || "#545454",
            size: settings.fontSize || 10,
            family: "Roboto"
        };

        var options = {
            colors: colors,
            title: null,
            subtitle: null,
            shadowSize: settings.shadowSize || 4,
            HtmlText: false,
            grid: {
                hoverable: true,
                mouseActiveRadius: 10,
                autoHighlight: ctx.tooltipIndividual === true
            },
            selection : { mode : ctx.isMobile ? null : 'x' },
            legend : {
                show: false
            }
        };

        if (this.chartType === 'line' || this.chartType === 'bar') {
            options.xaxis = {
                mode: 'time',
                timezone: 'browser',
                font: angular.copy(font),
                labelFont: angular.copy(font)
            };
            options.yaxis = {
                font: angular.copy(font),
                labelFont: angular.copy(font)
            };
            if (settings.xaxis) {
                if (settings.xaxis.showLabels === false) {
                    options.xaxis.tickFormatter = function() {
                        return '';
                    };
                }
                options.xaxis.font.color = settings.xaxis.color || options.xaxis.font.color;
                options.xaxis.label = settings.xaxis.title || null;
                options.xaxis.labelFont.color = options.xaxis.font.color;
                options.xaxis.labelFont.size = options.xaxis.font.size+2;
                options.xaxis.labelFont.weight = "bold";
            }
            if (settings.yaxis) {
                if (settings.yaxis.showLabels === false) {
                    options.yaxis.tickFormatter = function() {
                        return '';
                    };
                } else if (ctx.trackUnits && ctx.trackUnits.length > 0) {
                    options.yaxis.tickFormatter = function(value, axis) {
                        var factor = axis.tickDecimals ? Math.pow(10, axis.tickDecimals) : 1,
                            formatted = "" + Math.round(value * factor) / factor;
                        if (axis.tickDecimals != null) {
                            var decimal = formatted.indexOf("."),
                                precision = decimal === -1 ? 0 : formatted.length - decimal - 1;

                            if (precision < axis.tickDecimals) {
                                formatted = (precision ? formatted : formatted + ".") + ("" + factor).substr(1, axis.tickDecimals - precision);
                            }
                        }
                        formatted += ' ' + tbFlot.ctx.trackUnits;
                        return formatted;
                    };
                }
                options.yaxis.font.color = settings.yaxis.color || options.yaxis.font.color;
                options.yaxis.label = settings.yaxis.title || null;
                options.yaxis.labelFont.color = options.yaxis.font.color;
                options.yaxis.labelFont.size = options.yaxis.font.size+2;
                options.yaxis.labelFont.weight = "bold";
            }

            options.grid.borderWidth = 1;
            options.grid.color = settings.fontColor || "#545454";

            if (settings.grid) {
                options.grid.color = settings.grid.color || "#545454";
                options.grid.backgroundColor = settings.grid.backgroundColor || null;
                options.grid.tickColor = settings.grid.tickColor || "#DDDDDD";
                options.grid.borderWidth = angular.isDefined(settings.grid.outlineWidth) ?
                    settings.grid.outlineWidth : 1;
                if (settings.grid.verticalLines === false) {
                    options.xaxis.tickLength = 0;
                }
                if (settings.grid.horizontalLines === false) {
                    options.yaxis.tickLength = 0;
                }
            }

            options.crosshair = {
                mode: 'x'
            }

            options.series = {
                stack: settings.stack === true
            }

            if (this.chartType === 'bar') {
                options.series.lines = {
                        show: false,
                        fill: false,
                        steps: false
                }
                options.series.bars ={
                        show: true,
                        barWidth: ctx.timeWindow.interval * 0.6,
                        lineWidth: 0,
                        fill: 0.9
                }
            }

            options.xaxis.min = ctx.timeWindow.minTime;
            options.xaxis.max = ctx.timeWindow.maxTime;
        } else if (this.chartType === 'pie') {
            options.series = {
                pie: {
                    show: true,
                    label: {
                        show: settings.showLabels === true
                    },
                    radius: settings.radius || 1,
                    innerRadius: settings.innerRadius || 0,
                    stroke: {
                        color: '#fff',
                        width: 0
                    },
                    tilt: settings.tilt || 1,
                    shadow: {
                        left: 5,
                        top: 15,
                        alpha: 0.02
                    }
                }
            }
            if (settings.stroke) {
                options.series.pie.stroke.color = settings.stroke.color || '#fff';
                options.series.pie.stroke.width = settings.stroke.width || 0;
            }

            if (options.series.pie.label.show) {
                options.series.pie.label.formatter = function (label, series) {
                    return "<div class='pie-label'>" + series.dataKey.label + "<br/>" + Math.round(series.percent) + "%</div>";
                }
                options.series.pie.label.radius = 3/4;
                options.series.pie.label.background = {
                     opacity: 0.8
                };
            }
        }

        //Experimental
        this.ctx.animatedPie = settings.animatedPie === true;

        this.options = options;

        this.checkMouseEvents();

        if (this.chartType === 'pie' && this.ctx.animatedPie) {
            this.ctx.pieDataAnimationDuration = 250;
            this.ctx.pieData = angular.copy(this.ctx.data);
            this.ctx.pieRenderedData = [];
            this.ctx.pieTargetData = [];
            for (i in this.ctx.data) {
                this.ctx.pieTargetData[i] = (this.ctx.data[i].data && this.ctx.data[i].data[0])
                    ? this.ctx.data[i].data[0][1] : 0;
            }
            this.pieDataRendered();
            this.ctx.plot = $.plot(this.ctx.$container, this.ctx.pieData, this.options);
        } else {
            this.ctx.plot = $.plot(this.ctx.$container, this.ctx.data, this.options);
        }
    }

    update() {
        if (!this.isMouseInteraction && this.ctx.plot) {
            if (this.chartType === 'line' || this.chartType === 'bar') {
                this.options.xaxis.min = this.ctx.timeWindow.minTime;
                this.options.xaxis.max = this.ctx.timeWindow.maxTime;
                this.ctx.plot.getOptions().xaxes[0].min = this.ctx.timeWindow.minTime;
                this.ctx.plot.getOptions().xaxes[0].max = this.ctx.timeWindow.maxTime;
                if (this.chartType === 'bar') {
                    this.options.series.bars.barWidth = this.ctx.timeWindow.interval * 0.6;
                    this.ctx.plot.getOptions().series.bars.barWidth = this.ctx.timeWindow.interval * 0.6;
                }
                this.ctx.plot.setData(this.ctx.data);
                this.ctx.plot.setupGrid();
                this.ctx.plot.draw();
            } else if (this.chartType === 'pie') {
                if (this.ctx.animatedPie) {
                    this.nextPieDataAnimation(true);
                } else {
                    this.ctx.plot.setData(this.ctx.data);
                    this.ctx.plot.draw();
                }
            }
        }
    }

    resize() {
        this.ctx.plot.resize();
        if (this.chartType !== 'pie') {
            this.ctx.plot.setupGrid();
        }
        this.ctx.plot.draw();
    }

    static get pieSettingsSchema() {
        return {
            "schema": {
                "type": "object",
                "title": "Settings",
                "properties": {
                    "radius": {
                        "title": "Radius",
                        "type": "number",
                        "default": 1
                    },
                    "innerRadius": {
                        "title": "Inner radius",
                        "type": "number",
                        "default": 0
                    },
                    "tilt": {
                        "title": "Tilt",
                        "type": "number",
                        "default": 1
                    },
                    "animatedPie": {
                        "title": "Enable pie animation (experimental)",
                        "type": "boolean",
                        "default": false
                    },
                    "stroke": {
                        "title": "Stroke",
                        "type": "object",
                        "properties": {
                            "color": {
                                "title": "Color",
                                "type": "string",
                                "default": ""
                            },
                            "width": {
                                "title": "Width (pixels)",
                                "type": "number",
                                "default": 0
                            }
                        }
                    },
                    "showLabels": {
                        "title": "Show labels",
                        "type": "boolean",
                        "default": false
                    },
                    "fontColor": {
                        "title": "Font color",
                        "type": "string",
                        "default": "#545454"
                    },
                    "fontSize": {
                        "title": "Font size",
                        "type": "number",
                        "default": 10
                    }
                },
                "required": []
            },
            "form": [
                "radius",
                "innerRadius",
                "animatedPie",
                "tilt",
                {
                    "key": "stroke",
                    "items": [
                        {
                            "key": "stroke.color",
                            "type": "color"
                        },
                        "stroke.width"
                    ]
                },
                "showLabels",
                {
                    "key": "fontColor",
                    "type": "color"
                },
                "fontSize"
            ]
        }
    }

    static get settingsSchema() {
        return {
            "schema": {
                "type": "object",
                "title": "Settings",
                "properties": {
                    "stack": {
                        "title": "Stacking",
                        "type": "boolean",
                        "default": false
                    },
                    "shadowSize": {
                        "title": "Shadow size",
                        "type": "number",
                        "default": 4
                    },
                    "fontColor": {
                        "title": "Font color",
                        "type": "string",
                        "default": "#545454"
                    },
                    "fontSize": {
                        "title": "Font size",
                        "type": "number",
                        "default": 10
                    },
                    "tooltipIndividual": {
                        "title": "Hover individual points",
                        "type": "boolean",
                        "default": false
                    },
                    "tooltipCumulative": {
                        "title": "Show cumulative values in stacking mode",
                        "type": "boolean",
                        "default": false
                    },
                    "grid": {
                        "title": "Grid settings",
                        "type": "object",
                        "properties": {
                            "color": {
                                "title": "Primary color",
                                "type": "string",
                                "default": "#545454"
                            },
                            "backgroundColor": {
                                "title": "Background color",
                                "type": "string",
                                "default": null
                            },
                            "tickColor": {
                                "title": "Ticks color",
                                "type": "string",
                                "default": "#DDDDDD"
                            },
                            "outlineWidth": {
                                "title": "Grid outline/border width (px)",
                                "type": "number",
                                "default": 1
                            },
                            "verticalLines": {
                                "title": "Show vertical lines",
                                "type": "boolean",
                                "default": true
                            },
                            "horizontalLines": {
                                "title": "Show horizontal lines",
                                "type": "boolean",
                                "default": true
                            }
                        }
                    },
                    "xaxis": {
                        "title": "X axis settings",
                        "type": "object",
                        "properties": {
                            "showLabels": {
                                "title": "Show labels",
                                "type": "boolean",
                                "default": true
                            },
                            "title": {
                                "title": "Axis title",
                                "type": "string",
                                "default": null
                            },
                            "titleAngle": {
                                "title": "Axis title's angle in degrees",
                                "type": "number",
                                "default": 0
                            },
                            "color": {
                                "title": "Ticks color",
                                "type": "string",
                                "default": null
                            }
                        }
                    },
                    "yaxis": {
                        "title": "Y axis settings",
                        "type": "object",
                        "properties": {
                            "showLabels": {
                                "title": "Show labels",
                                "type": "boolean",
                                "default": true
                            },
                            "title": {
                                "title": "Axis title",
                                "type": "string",
                                "default": null
                            },
                            "titleAngle": {
                                "title": "Axis title's angle in degrees",
                                "type": "number",
                                "default": 0
                            },
                            "color": {
                                "title": "Ticks color",
                                "type": "string",
                                "default": null
                            }
                        }
                    }
                },
                "required": []
            },
            "form": [
                "stack",
                "shadowSize",
                {
                    "key": "fontColor",
                    "type": "color"
                },
                "fontSize",
                "tooltipIndividual",
                "tooltipCumulative",
                {
                    "key": "grid",
                    "items": [
                        {
                            "key": "grid.color",
                            "type": "color"
                        },
                        {
                            "key": "grid.backgroundColor",
                            "type": "color"
                        },
                        {
                            "key": "grid.tickColor",
                            "type": "color"
                        },
                        "grid.outlineWidth",
                        "grid.verticalLines",
                        "grid.horizontalLines"
                    ]
                },
                {
                    "key": "xaxis",
                    "items": [
                        "xaxis.showLabels",
                        "xaxis.title",
                        "xaxis.titleAngle",
                        {
                            "key": "xaxis.color",
                            "type": "color"
                        }
                    ]
                },
                {
                    "key": "yaxis",
                    "items": [
                        "yaxis.showLabels",
                        "yaxis.title",
                        "yaxis.titleAngle",
                        {
                            "key": "yaxis.color",
                            "type": "color"
                        }
                    ]
                }

            ]
        }
    }

    static get pieDatakeySettingsSchema() {
        return {}
    }

    static datakeySettingsSchema(defaultShowLines) {
        return {
                "schema": {
                "type": "object",
                    "title": "DataKeySettings",
                    "properties": {
                    "showLines": {
                        "title": "Show lines",
                            "type": "boolean",
                            "default": defaultShowLines
                    },
                    "fillLines": {
                        "title": "Fill lines",
                            "type": "boolean",
                            "default": false
                    },
                    "showPoints": {
                        "title": "Show points",
                            "type": "boolean",
                            "default": false
                    }
                },
                "required": ["showLines", "fillLines", "showPoints"]
            },
                "form": [
                "showLines",
                "fillLines",
                "showPoints"
            ]
        }
    }

    checkMouseEvents() {
        var enabled = !this.ctx.isMobile &&  !this.ctx.isEdit;
        if (angular.isUndefined(this.mouseEventsEnabled) || this.mouseEventsEnabled != enabled) {
            this.mouseEventsEnabled = enabled;
            if (enabled) {
                this.enableMouseEvents();
            } else {
                this.disableMouseEvents();
            }
            if (this.ctx.plot) {
                this.ctx.plot.destroy();
                if (this.chartType === 'pie' && this.ctx.animatedPie) {
                    this.ctx.plot = $.plot(this.ctx.$container, this.ctx.pieData, this.options);
                } else {
                    this.ctx.plot = $.plot(this.ctx.$container, this.ctx.data, this.options);
                }
            }
        }
    }

    destroy() {
        if (this.ctx.plot) {
            this.ctx.plot.destroy();
            this.ctx.plot = null;
        }
    }

    enableMouseEvents() {
        this.ctx.$container.css('pointer-events','');
        this.ctx.$container.addClass('mouse-events');
        this.options.selection = { mode : 'x' };

        var tbFlot = this;

        if (!this.flotHoverHandler) {
            this.flotHoverHandler =  function (event, pos, item) {
                if (!tbFlot.ctx.tooltipIndividual || item) {

                    var multipleModeTooltip = !tbFlot.ctx.tooltipIndividual;

                    if (multipleModeTooltip) {
                        tbFlot.ctx.plot.unhighlight();
                    }

                    var pageX = pos.pageX;
                    var pageY = pos.pageY;

                    var tooltipHtml;

                    if (tbFlot.chartType === 'pie') {
                        tooltipHtml = tbFlot.ctx.tooltipFormatter(item);
                    } else {
                        var hoverInfo = tbFlot.getHoverInfo(tbFlot.ctx.plot.getData(), pos);
                        if (angular.isNumber(hoverInfo.time)) {
                            hoverInfo.seriesHover.sort(function (a, b) {
                                return b.value - a.value;
                            });
                            tooltipHtml = tbFlot.ctx.tooltipFormatter(hoverInfo, item ? item.seriesIndex : -1);
                        }
                    }

                    if (tooltipHtml) {
                        tbFlot.ctx.tooltip.html(tooltipHtml)
                            .css({top: pageY+5, left: 0})
                            .fadeIn(200);

                        var windowWidth = $( window ).width();  //eslint-disable-line
                        var tooltipWidth = tbFlot.ctx.tooltip.width();
                        var left = pageX+5;
                        if (windowWidth - pageX < tooltipWidth + 50) {
                            left = pageX - tooltipWidth - 10;
                        }
                        tbFlot.ctx.tooltip.css({
                            left: left
                        });

                        if (multipleModeTooltip) {
                            for (var i = 0; i < hoverInfo.seriesHover.length; i++) {
                                var seriesHoverInfo = hoverInfo.seriesHover[i];
                                tbFlot.ctx.plot.highlight(seriesHoverInfo.index, seriesHoverInfo.hoverIndex);
                            }
                        }
                    }

                } else {
                    tbFlot.ctx.tooltip.stop(true);
                    tbFlot.ctx.tooltip.hide();
                    tbFlot.ctx.plot.unhighlight();
                }
            };
            this.ctx.$container.bind('plothover', this.flotHoverHandler);
        }

        if (!this.flotSelectHandler) {
            this.flotSelectHandler =  function (event, ranges) {
                tbFlot.ctx.plot.clearSelection();
                tbFlot.ctx.timewindowFunctions.onUpdateTimewindow(ranges.xaxis.from, ranges.xaxis.to);
            };
            this.ctx.$container.bind('plotselected', this.flotSelectHandler);
        }
        if (!this.dblclickHandler) {
            this.dblclickHandler =  function () {
                tbFlot.ctx.timewindowFunctions.onResetTimewindow();
            };
            this.ctx.$container.bind('dblclick', this.dblclickHandler);
        }
        if (!this.mousedownHandler) {
            this.mousedownHandler =  function () {
                tbFlot.isMouseInteraction = true;
            };
            this.ctx.$container.bind('mousedown', this.mousedownHandler);
        }
        if (!this.mouseupHandler) {
            this.mouseupHandler =  function () {
                tbFlot.isMouseInteraction = false;
            };
            this.ctx.$container.bind('mouseup', this.mouseupHandler);
        }
        if (!this.mouseleaveHandler) {
            this.mouseleaveHandler =  function () {
                tbFlot.ctx.tooltip.stop(true);
                tbFlot.ctx.tooltip.hide();
                tbFlot.ctx.plot.unhighlight();
                tbFlot.isMouseInteraction = false;
            };
            this.ctx.$container.bind('mouseleave', this.mouseleaveHandler);
        }
    }

    disableMouseEvents() {
        this.ctx.$container.css('pointer-events','none');
        this.ctx.$container.removeClass('mouse-events');
        this.options.selection = { mode : null };

        if (this.flotHoverHandler) {
            this.ctx.$container.unbind('plothover', this.flotHoverHandler);
            this.flotHoverHandler = null;
        }

        if (this.flotSelectHandler) {
            this.ctx.$container.unbind('plotselected', this.flotSelectHandler);
            this.flotSelectHandler = null;
        }
        if (this.dblclickHandler) {
            this.ctx.$container.unbind('dblclick', this.dblclickHandler);
            this.dblclickHandler = null;
        }
        if (this.mousedownHandler) {
            this.ctx.$container.unbind('mousedown', this.mousedownHandler);
            this.mousedownHandler = null;
        }
        if (this.mouseupHandler) {
            this.ctx.$container.unbind('mouseup', this.mouseupHandler);
            this.mouseupHandler = null;
        }
        if (this.mouseleaveHandler) {
            this.ctx.$container.unbind('mouseleave', this.mouseleaveHandler);
            this.mouseleaveHandler = null;
        }
    }


    findHoverIndexFromData (posX, series) {
        var lower = 0;
        var upper = series.data.length - 1;
        var middle;
        var index = null;
        while (index === null) {
            if (lower > upper) {
                return Math.max(upper, 0);
            }
            middle = Math.floor((lower + upper) / 2);
            if (series.data[middle][0] === posX) {
                return middle;
            } else if (series.data[middle][0] < posX) {
                lower = middle + 1;
            } else {
                upper = middle - 1;
            }
        }
    }

    findHoverIndexFromDataPoints (posX, series, last) {
        var ps = series.datapoints.pointsize;
        var initial = last*ps;
        var len = series.datapoints.points.length;
        for (var j = initial; j < len; j += ps) {
            if ((!series.lines.steps && series.datapoints.points[initial] != null && series.datapoints.points[j] == null)
                || series.datapoints.points[j] > posX) {
                return Math.max(j - ps,  0)/ps;
            }
        }
        return j/ps - 1;
    }


    getHoverInfo (seriesList, pos) {
        var i, series, value, hoverIndex, hoverDistance, pointTime, minDistance, minTime;
        var last_value = 0;
        var results = {
            seriesHover: []
        };
        for (i = 0; i < seriesList.length; i++) {
            series = seriesList[i];
            hoverIndex = this.findHoverIndexFromData(pos.x, series);
            if (series.data[hoverIndex] && series.data[hoverIndex][0]) {
                hoverDistance = pos.x - series.data[hoverIndex][0];
                pointTime = series.data[hoverIndex][0];

                if (!minDistance
                    || (hoverDistance >= 0 && (hoverDistance < minDistance || minDistance < 0))
                    || (hoverDistance < 0 && hoverDistance > minDistance)) {
                    minDistance = hoverDistance;
                    minTime = pointTime;
                }
                if (series.stack) {
                    if (this.ctx.tooltipIndividual || !this.ctx.tooltipCumulative) {
                        value = series.data[hoverIndex][1];
                    } else {
                        last_value += series.data[hoverIndex][1];
                        value = last_value;
                    }
                } else {
                    value = series.data[hoverIndex][1];
                }

                if (series.stack) {
                    hoverIndex = this.findHoverIndexFromDataPoints(pos.x, series, hoverIndex);
                }
                results.seriesHover.push({
                    value: value,
                    hoverIndex: hoverIndex,
                    color: series.dataKey.color,
                    label: series.dataKey.label,
                    time: pointTime,
                    distance: hoverDistance,
                    index: i
                });
            }
        }
        results.time = minTime;
        return results;
    }

    pieDataRendered() {
        for (var i in this.ctx.pieTargetData) {
            var value = this.ctx.pieTargetData[i] ? this.ctx.pieTargetData[i] : 0;
            this.ctx.pieRenderedData[i] = value;
            if (!this.ctx.pieData[i].data[0]) {
                this.ctx.pieData[i].data[0] = [0,0];
            }
            this.ctx.pieData[i].data[0][1] = value;
        }
    }

    nextPieDataAnimation(start) {
        if (start) {
            this.finishPieDataAnimation();
            this.ctx.pieAnimationStartTime = this.ctx.pieAnimationLastTime = Date.now();
            for (var i in this.ctx.data) {
                this.ctx.pieTargetData[i] = (this.ctx.data[i].data && this.ctx.data[i].data[0])
                    ? this.ctx.data[i].data[0][1] : 0;
            }
        }
        if (this.ctx.pieAnimationCaf) {
            this.ctx.pieAnimationCaf();
            this.ctx.pieAnimationCaf = null;
        }
        var self = this;
        this.ctx.pieAnimationCaf = this.ctx.$scope.tbRaf(
            function () {
                self.onPieDataAnimation();
            }
        );
    }

    onPieDataAnimation() {
        var time = Date.now();
        var elapsed = time - this.ctx.pieAnimationLastTime;//this.ctx.pieAnimationStartTime;
        var progress = (time - this.ctx.pieAnimationStartTime) / this.ctx.pieDataAnimationDuration;
        if (progress >= 1) {
            this.finishPieDataAnimation();
        } else {
            if (elapsed >= 40) {
                for (var i in this.ctx.pieTargetData) {
                    var prevValue = this.ctx.pieRenderedData[i];
                    var targetValue = this.ctx.pieTargetData[i];
                    var value = prevValue + (targetValue - prevValue) * progress;
                    if (!this.ctx.pieData[i].data[0]) {
                        this.ctx.pieData[i].data[0] = [0,0];
                    }
                    this.ctx.pieData[i].data[0][1] = value;
                }
                this.ctx.plot.setData(this.ctx.pieData);
                this.ctx.plot.draw();
                this.ctx.pieAnimationLastTime = time;
            }
            this.nextPieDataAnimation(false);
        }
    }

    finishPieDataAnimation() {
        this.pieDataRendered();
        this.ctx.plot.setData(this.ctx.pieData);
        this.ctx.plot.draw();
    }
}

/* eslint-enable angular/angularelement */