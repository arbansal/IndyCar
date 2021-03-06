import React from "react";
import SpeedAnomalyComponent from "./SpeedAnomalyComponent";
import {Card} from "@blueprintjs/core";
import "./AnomalyWrapper.css";
import {SocketService} from "../../services/SocketService";

export default class AnomalyWrapper extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            carNumbers: [
                20, 21, 13, 98, 19, 6, 33, 24, 26, 7, 60, 27, 17, 15, 10,
                64, 25, 59, 32, 28, 4, 3, 18, 22, 12, 1, 9, 14, 23, 30, 29, 88, 66
            ].sort(function (a, b) {
                return a - b
            }),
            selectedCarNumber: 21,
            anomalyLabel: undefined
        };
        this.socket = new SocketService();
    }

    onReceiveChartData = (data) => {
        if (data.anomalyLabel &&
            (!this.state.anomalyLabel || this.state.anomalyLabel.uuid !== data.anomalyLabel.uuid)) {
            this.setState({
                anomalyLabel: data.anomalyLabel
            });
        }

        if (!data.anomalyLabel && this.state.anomalyLabel) {
            this.setState({
                anomalyLabel: undefined
            });
        }
    };

    subscribe = () => {
        this.socket.send("EVENT_SUB", {
            roomName: "anomaly_" + this.state.selectedCarNumber
        });
        this.socket.subscribe("anomaly_" + this.state.selectedCarNumber, this.onReceiveChartData);
    };

    componentDidMount() {
        this.subscribe();
    }

    onCarChange = (event) => {
        this.socket.send("EVENT_UNSUB", {
            roomName: "anomaly_" + this.state.selectedCarNumber
        });
        this.socket.unsubscribe("anomaly_" + this.state.selectedCarNumber, this.onReceiveChartData);
        this.setState({
            selectedCarNumber: event.target.value
        }, this.subscribe);
    };

    render() {
        return (
            <div className="ic-section ic-anomaly-wrapper">
                <Card>
                    <h5>Anomaly Scores</h5>
                    <div className="ic-anomaly-header">
                        <div className="ic-anomaly-selection">
                            <label className="pt-label">
                                Car
                                <div className="pt-select">
                                    <select onChange={this.onCarChange} value={this.state.selectedCarNumber}>
                                        {
                                            this.state.carNumbers.map(carNum => {
                                                return <option value={carNum} key={carNum}>{carNum}</option>
                                            })
                                        }
                                    </select>
                                </div>
                            </label>
                        </div>
                        <div className="ic-anomaly-label-wrapper">
                            {
                                this.state.anomalyLabel
                                &&
                                <div className="ic-anomaly-label">
                                    {this.state.anomalyLabel.label}
                                    LABEL
                                </div>
                            }
                        </div>
                    </div>

                    <SpeedAnomalyComponent carNumber={this.state.selectedCarNumber}
                                           metric={"SPEED"}
                                           rawDataColor="#1565C0"
                                           hideX={true}
                                           key={this.state.selectedCarNumber + "SPEED"}/>
                    <SpeedAnomalyComponent carNumber={this.state.selectedCarNumber}
                                           metric={"RPM"}
                                           hideX={true}
                                           rawDataColor="#2E7D32"
                                           key={this.state.selectedCarNumber + "RPM"}/>
                    <SpeedAnomalyComponent carNumber={this.state.selectedCarNumber}
                                           metric={"THROTTLE"}
                                           rawDataColor="#673AB7"
                                           key={this.state.selectedCarNumber + "THROTTLE"}/>
                </Card>
            </div>
        );
    }
}