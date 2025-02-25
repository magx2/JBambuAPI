package pl.grzeslowski.jbambuapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PrinterState(PrintDetails printDetails) {

    public static PrinterState merge(PrinterState previous, PrinterState next) {
        if(previous == null) return next;
        if(next == null) return previous;
        return new PrinterState(PrintDetails.merge(previous.printDetails, next.printDetails));
    }

    public record PrintDetails(
            @JsonProperty("upgrade_state") UpgradeState upgradeState,
            @JsonProperty("ipcam") IpCam ipCam,
            @JsonProperty("xcam") XCam xCam,
            @JsonProperty("upload") Upload upload,
            @JsonProperty("net") Net net,
            @JsonProperty("nozzle_temper") double nozzleTemperature,
            @JsonProperty("nozzle_target_temper") double nozzleTargetTemperature,
            @JsonProperty("bed_temper") double bedTemperature,
            @JsonProperty("bed_target_temper") double bedTargetTemperature,
            @JsonProperty("chamber_temper") int chamberTemperature,
            @JsonProperty("mc_print_stage") String mcPrintStage,
            @JsonProperty("mc_percent") int mcPercent,
            @JsonProperty("mc_remaining_time") int mcRemainingTime,
            @JsonProperty("wifi_signal") String wifiSignal,
            @JsonProperty("command") String command,
            @JsonProperty("msg") int message,
            @JsonProperty("sequence_id") String sequenceId
    ) {
        public static PrintDetails merge(PrintDetails previous, PrintDetails next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new PrintDetails(
                    UpgradeState.merge(previous.upgradeState, next.upgradeState),
                    IpCam.merge(previous.ipCam, next.ipCam),
                    XCam.merge(previous.xCam, next.xCam),
                    Upload.merge(previous.upload, next.upload),
                    Net.merge(previous.net, next.net),
                    next.nozzleTemperature != 0 ? next.nozzleTemperature : previous.nozzleTemperature,
                    next.nozzleTargetTemperature != 0 ? next.nozzleTargetTemperature : previous.nozzleTargetTemperature,
                    next.bedTemperature != 0 ? next.bedTemperature : previous.bedTemperature,
                    next.bedTargetTemperature != 0 ? next.bedTargetTemperature : previous.bedTargetTemperature,
                    next.chamberTemperature != 0 ? next.chamberTemperature : previous.chamberTemperature,
                    Optional.ofNullable(next.mcPrintStage).orElse(previous.mcPrintStage),
                    next.mcPercent != 0 ? next.mcPercent : previous.mcPercent,
                    next.mcRemainingTime != 0 ? next.mcRemainingTime : previous.mcRemainingTime,
                    Optional.ofNullable(next.wifiSignal).orElse(previous.wifiSignal),
                    Optional.ofNullable(next.command).orElse(previous.command),
                    next.message != 0 ? next.message : previous.message,
                    Optional.ofNullable(next.sequenceId).orElse(previous.sequenceId)
            );
        }
    }

    public record UpgradeState(
            @JsonProperty("sequence_id") int sequenceId,
            @JsonProperty("status") String status,
            @JsonProperty("message") String message
    ) {
        public static UpgradeState merge(UpgradeState previous, UpgradeState next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new UpgradeState(
                    next.sequenceId != 0 ? next.sequenceId : previous.sequenceId,
                    Optional.ofNullable(next.status).orElse(previous.status),
                    Optional.ofNullable(next.message).orElse(previous.message)
            );
        }
    }

    public record IpCam(
            @JsonProperty("ipcam_dev") String ipcamDev,
            @JsonProperty("ipcam_record") String ipcamRecord,
            @JsonProperty("timelapse") String timelapse,
            @JsonProperty("resolution") String resolution,
            @JsonProperty("tutk_server") String tutkServer,
            @JsonProperty("mode_bits") int modeBits
    ) {
        public static IpCam merge(IpCam previous, IpCam next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new IpCam(
                    Optional.ofNullable(next.ipcamDev).orElse(previous.ipcamDev),
                    Optional.ofNullable(next.ipcamRecord).orElse(previous.ipcamRecord),
                    Optional.ofNullable(next.timelapse).orElse(previous.timelapse),
                    Optional.ofNullable(next.resolution).orElse(previous.resolution),
                    Optional.ofNullable(next.tutkServer).orElse(previous.tutkServer),
                    next.modeBits != 0 ? next.modeBits : previous.modeBits
            );
        }
    }

    public record XCam(
            @JsonProperty("buildplate_marker_detector") boolean buildplateMarkerDetector
    ) {
        public static XCam merge(XCam previous, XCam next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new XCam(next.buildplateMarkerDetector || previous.buildplateMarkerDetector);
        }
    }

    public record Upload(
            @JsonProperty("status") String status,
            @JsonProperty("progress") int progress,
            @JsonProperty("message") String message
    ) {
        public static Upload merge(Upload previous, Upload next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new Upload(
                    Optional.ofNullable(next.status).orElse(previous.status),
                    next.progress != 0 ? next.progress : previous.progress,
                    Optional.ofNullable(next.message).orElse(previous.message)
            );
        }
    }

    public record Net(
            @JsonProperty("conf") int conf,
            @JsonProperty("info") List<Map<String, Object>> info
    ) {
        public static Net merge(Net previous, Net next) {
            if(previous == null) return next;
            if(next == null) return previous;
            return new Net(
                    next.conf != 0 ? next.conf : previous.conf,
                    Optional.ofNullable(next.info).orElse(previous.info)
            );
        }
    }
}
