// ----------------------------------------------------------------[Package]----------------------------------------------------------------//
package frc.robot.subsystems.drivebase;
// ---------------------------------------------------------------[Libraries]---------------------------------------------------------------//
import com.pathplanner.lib.server.PathPlannerServer;
import com.pathplanner.lib.auto.SwerveAutoBuilder;
import com.pathplanner.lib.PathPlannerTrajectory;
import com.pathplanner.lib.auto.PIDConstants;

import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Pose2d;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import frc.lib.motion.legacycontrol.DrivebaseModule;
import frc.robot.subsystems.drivebase.Constants.Values.Chassis;
import frc.robot.subsystems.drivebase.Constants.Values.Maximum;

import static frc.robot.subsystems.drivebase.Constants.Hardware;
import static frc.robot.subsystems.drivebase.Constants.Values;
import static frc.robot.Constants.LOGGER;

// -------------------------------------------------------[Drivebase Subsystem Class]-------------------------------------------------------//
/**
 * 
 * 
 * <h1> DrivebaseSubsystem </h1>
 *
 *
 * <p> {@link frc.robot.subsystems.drivebase.Constants Constants} based implementation of swerve drivebase which utilizes
 * {@link frc.lib.motion.legacycontrol.LinearSystemModule modules} based on {@link edu.wpi.first.math.system.LinearSystemLoop LinearSystemLoop} to achieve more
 * accurate azimuth positioning. This subsystem is entirely static, meaning only one instance will exist and this Instance is obtained
 * by calling {@link #getInstance()} from a static context. </p>
 *
 * @author Cody Washington (@Jelatinone)
 */
public final class DrivebaseSubsystem extends SubsystemBase implements Consumer<SwerveModuleState[]>, Supplier<Pose2d> {
    // --------------------------------------------------------------[Constants]--------------------------------------------------------------//
    private static final List<DrivebaseModule> MODULES = List.of(
        Hardware.Modules.FL_Module.Components.MODULE,
        Hardware.Modules.FR_Module.Components.MODULE,
        Hardware.Modules.RL_Module.Components.MODULE,
        Hardware.Modules.RR_Module.Components.MODULE);

    private static final SwerveDriveKinematics KINEMATICS = new SwerveDriveKinematics(
        new Translation2d( (Values.Chassis.DRIVETRAIN_WIDTH) / (2), 
                           (Values.Chassis.DRIVETRAIN_WIDTH) / (2)),
        new Translation2d( (Values.Chassis.DRIVETRAIN_WIDTH) / (2),
                          -(Values.Chassis.DRIVETRAIN_WIDTH) / (2)),
        new Translation2d(-(Values.Chassis.DRIVETRAIN_WIDTH) / (2),
                           (Values.Chassis.DRIVETRAIN_WIDTH) / (2)),
        new Translation2d(-(Values.Chassis.DRIVETRAIN_WIDTH) / (2),
                          -(Values.Chassis.DRIVETRAIN_WIDTH) / (2)));

    private static final SwerveDrivePoseEstimator POSE_ESTIMATOR = new SwerveDrivePoseEstimator(
        KINEMATICS,
        Hardware.GYROSCOPE.getRotation2d(),
        getModulePositions(),
        new Pose2d());

    private static final Field2d FIELD = new Field2d();
    // ---------------------------------------------------------------[Fields]----------------------------------------------------------------//
    private static DrivebaseSubsystem INSTANCE = (null);    
    private static Boolean LockingEnabled = (false);    
    private static Boolean FieldOriented = (false);
    private static Double TimeInterval = (0.0);
    // ------------------------------------------------------------[Constructors]-------------------------------------------------------------//
    /**
     * Constructor.
     */
    private DrivebaseSubsystem() {
        PathPlannerServer.startServer(Values.Ports.PATHPLANNER_SERVER_PORT);
        addChild(("FL-Module[0]"),Hardware.Modules.FL_Module.Components.MODULE);
        addChild(("FR-Module[1]"),Hardware.Modules.FR_Module.Components.MODULE);
        addChild(("RL-Module[2]"),Hardware.Modules.RL_Module.Components.MODULE);
        addChild(("RR-Module[3]"),Hardware.Modules.RR_Module.Components.MODULE);
        LOGGER.recordOutput(("Drivebase/Orientation (rightX) Input"), (0.0));        
        LOGGER.recordOutput(("Drivebase/Translation (leftX) Input"), (0.0));
        LOGGER.recordOutput(("Drivebase/Translation (leftY) Input"), (0.0));        
    }
    // ---------------------------------------------------------------[Methods]---------------------------------------------------------------//

    /**
     * Periodically updates the robot's estimated position, and Update the module and drivebase telemetry for all modules within the system.
     */
    @Override
    public void periodic() {
        double IntervalTime;
        if (TimeInterval != (0)) {
            var RealTime = Timer.getFPGATimestamp();
            IntervalTime = RealTime - TimeInterval;
            TimeInterval = RealTime;
        } else {
            IntervalTime = (0.02);
        }
        new Thread(() -> FIELD.setRobotPose(POSE_ESTIMATOR.updateWithTime((IntervalTime), Hardware.GYROSCOPE.getRotation2d(), getModulePositions()))).start();
        SmartDashboard.putNumber(("Drivebase/ResponseTime"),IntervalTime);
        LOGGER.recordOutput(("Drivebase/ResponseTime"),IntervalTime);
    }

    /**
     * Periodically updates the robot's estimated position, and Update the module and drivebase telemetry for all modules within the system.
     */
    @Override
    public void simulationPeriodic() {
        double IntervalTime;
        if (TimeInterval != (0)) {
            var RealTime = Timer.getFPGATimestamp();
            IntervalTime = RealTime - TimeInterval;
            TimeInterval = RealTime;
        } else {
            IntervalTime = (0.02);
        }
        new Thread(() -> FIELD.setRobotPose(POSE_ESTIMATOR.updateWithTime((IntervalTime), Hardware.GYROSCOPE.getRotation2d(), getModulePositions()))).start();
        SmartDashboard.putNumber(("Drivebase/ResponseTime"),IntervalTime);
        LOGGER.recordOutput(("Drivebase/ResponseTime"),IntervalTime);
    }

    /**
     * Consume an array of demand states for each module.
     *
     * @param Demand The states to demand for each module, paired in order with each module.
     */
    public void accept(final SwerveModuleState[] Demand) {
        set(Arrays.asList(Demand), () -> (false));
    }

    /**
     * Reset odometry with a new {@link edu.wpi.first.math.geometry.Pose2d Pose2d} relative to the field
     *
     * @param FieldRelativePose The actual position the robot should be placed at
     */
    public synchronized void reset(Pose2d FieldRelativePose) {
        POSE_ESTIMATOR.resetPosition(Hardware.GYROSCOPE.getRotation2d(), getModulePositions(), FieldRelativePose);
    }

    /**
     * Ceases all drivebase motion immediately, can be called again immediately with {@link #set(List, Supplier)}
     */
    public static synchronized void stop() {
        MODULES.stream().sequential().forEachOrdered(DrivebaseModule::stop);    
    }

    /**
    * Post all telemtry updates from the current time to the previous call to this method
    */
    public static void post() {
        var FieldPose = FIELD.getRobotPose();
        SmartDashboard.putNumber(("Drivebase/Translation"),FieldPose.getTranslation().getNorm());
        SmartDashboard.putNumber(("Drivebase/Rotation"),FieldPose.getRotation().getDegrees());
        SmartDashboard.putNumber(("Drivebase/Heading"),Hardware.GYROSCOPE.getYaw());
        LOGGER.recordOutput(("Drivebase/MeasuredStates"), getMeasuredModuleStates());        
        LOGGER.recordOutput(("Drivebase/DemandStates"), getDemandModuleStates());
        LOGGER.recordOutput(("Drivebase/OutputStates"), getOutputModuleStates());   
        LOGGER.recordOutput(("Drivebase/Heading"), Hardware.GYROSCOPE.getYaw());    
        LOGGER.recordOutput(("Drivebase/Pose"), FieldPose);   
    }
    // --------------------------------------------------------------[Mutators]---------------------------------------------------------------//
    /**
     * Set and  demand states based on translation and orientation joystick inputs to generate module states
     *
     * @param Translation_X Demand on the X-axis
     * @param Translation_Y Demand on the Y-axis
     * @param Orientation   The desired rotation of the front face
     * @param ControlType   Whether to use OpenLoop control
     */
    public static synchronized void set(final Double Translation_X, final Double Translation_Y, final Double Orientation, final Supplier<Boolean> ControlType) {
        LOGGER.recordOutput(("Drivebase/Translation (leftX) Input"), Translation_X);
        LOGGER.recordOutput(("Drivebase/Translation (leftY) Input"), Translation_Y);
        LOGGER.recordOutput(("Drivebase/Orientation (rightX) Input"), Orientation);
        SmartDashboard.putNumber(("Drivebase/Translation (leftX) Input"), Translation_X);
        SmartDashboard.putNumber(("Drivebase/Translation (leftY) Input"), Translation_Y);
        SmartDashboard.putNumber(("Drivebase/Orientation (rightX) Input"), Orientation);
        if((Math.abs(Translation_X) <= (2e-2)) && (Math.abs(Translation_Y) <= (2e-2)) && (Math.abs(Orientation) <= (2e-2))) {
            if (LockingEnabled) {
                set();
            } else {
                stop();
            }
        } else {
            set((List.of(KINEMATICS.toSwerveModuleStates(
                (FieldOriented)?
                    (ChassisSpeeds.fromFieldRelativeSpeeds(Translation_Y, Translation_X, Orientation, Hardware.GYROSCOPE.getRotation2d())):
                    (new ChassisSpeeds(Translation_Y, Translation_X, Orientation))))), 
                ControlType
            );
        }
        post();
    }

    /**
     * Set Module states given a list of swerve module states and a control type
     * @param Demand      Module state demands
     * @param ControlType Whether to use OpenLoop control
     */
    public static synchronized void set(List<SwerveModuleState> Demand, final Supplier<Boolean> ControlType) {
        var MeasuredStateIterator = MODULES.stream().map(DrivebaseModule::getMeasuredModuleState).iterator();
        Demand.stream().sequential().forEachOrdered((State) -> 
            State = DrivebaseModule.optimize(State, MeasuredStateIterator.next().angle));
        var DemandArray = Demand.toArray(SwerveModuleState[]::new);
        SwerveDriveKinematics.desaturateWheelSpeeds(DemandArray, Maximum.ROBOT_MAXIMUM_X_TRANSLATION_OUTPUT);
        var DemandIterator = Demand.iterator();        
        MODULES.stream().sequential().forEachOrdered((Module) ->  {
            Module.set(DemandIterator.next(), ControlType);
            Module.post();
        });
    }

    /**
     * Set module states given a list of swerve module states
     *
     * @param Demand Module state demands
     */
    public static synchronized void set(final List<SwerveModuleState> Demand) {
        set(Demand, () -> false);
    }

    /**
     * Set module's into a default, "x-locking" position that prevents drivebase movement when idle
     */
    public static synchronized void set() {
        set(List.of(
        new SwerveModuleState((0.0), new Rotation2d(Units.degreesToRadians((315)))),
        new SwerveModuleState((0.0), new Rotation2d(Units.degreesToRadians((45)))),
        new SwerveModuleState((0.0), new Rotation2d(Units.degreesToRadians((225)))),
        new SwerveModuleState((0.0), new Rotation2d(Units.degreesToRadians((135))))),
        () -> (true));
    }

    /**
     * Set whether the robot is using field oriented control
     *
     * @param isFieldOriented Whether field oriented control is enabled
     */
    public static void setFieldOriented(final Boolean isFieldOriented) {
        FieldOriented = isFieldOriented;
    }

    /**
     * Toggles (switches) the current state of Field Orientation
     */
    public static void toggleFieldOriented() {
        FieldOriented = !FieldOriented;
    }

    /**
     * Set whether the robot will x-lock when inputs are idle
     * 
     * @param isLockingEnabled Whether Locking is enabled
     */
    public static void setLockingEnabled(final Boolean isLockingEnabled) {
        LockingEnabled = isLockingEnabled;
    }

    /**
     * Toggles (switches) the current state of Lock Enabled
     */
    public static void toggleLockingEnabled() {
        LockingEnabled = !LockingEnabled;
    }
    // --------------------------------------------------------------[Accessors]--------------------------------------------------------------//

    /**
     * Obtain an autonomous command which follows a trajectory
     *
     * @param Demand   The demand pathplanner trajectory
     * @param EventMap An event map to pair to execute at given markers along the path
     * @param IsBlue   Whether the robot is on the blue alliance or not, flips the side of the robot
     * @return Autonomous trajectory following command
     */
    public static synchronized Command getAutonomousCommand(final PathPlannerTrajectory Demand, final HashMap<String, Command> EventMap, final Boolean IsBlue) {
        MODULES.stream().sequential().forEachOrdered(DrivebaseModule::reset);
        return new SwerveAutoBuilder(
            INSTANCE,
            INSTANCE::reset,
            KINEMATICS,
            new PIDConstants(Constants.Values.PathPlanner.TRANSLATION_KP, Constants.Values.PathPlanner.TRANSLATION_KI, Constants.Values.PathPlanner.TRANSLATION_KD),
            new PIDConstants(Constants.Values.PathPlanner.ROTATION_KP, Constants.Values.PathPlanner.ROTATION_KI, Constants.Values.PathPlanner.ROTATION_KD),
            INSTANCE,
            EventMap,
            IsBlue,
            INSTANCE)
        .fullAuto(Demand);
    }

    /**
     * Get an array representing the measured {@link edu.wpi.first.math.kinematics.SwerveModulePosition position} of each module in order
     *
     * @return An array of {@link edu.wpi.first.math.kinematics.SwerveModulePosition SwerveModulePosition}
     */
    public static SwerveModulePosition[] getModulePositions() {
        return MODULES.stream().sequential().map(
            (Module) -> new SwerveModulePosition(Values.ComponentData.SCALE_FACTOR * (Module.getMeasuredVelocity()) * Values.Chassis.DRIVETRAIN_LINEAR_GEAR_RATIO * Values.Chassis.WHEEL_PERIMETER, Module.getMeasuredPosition()))
            .toArray(SwerveModulePosition[]::new);
    }

    /**
     * Get an array representing the measured {@link edu.wpi.first.math.kinematics.SwerveModuleState state} of each module in order
     *
     * @return An array of {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleStates}
     */
    public static SwerveModuleState[] getMeasuredModuleStates() {
        return MODULES.stream().sequential().map(DrivebaseModule::getMeasuredModuleState).toArray(SwerveModuleState[]::new);
    }

    /**
     * Get an array representing the demand {@link edu.wpi.first.math.kinematics.SwerveModuleState state} of each module in order
     *
     * @return An array of {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleStates}
     */
    public static SwerveModuleState[] getDemandModuleStates() {
        return MODULES.stream().sequential().map(DrivebaseModule::getDemandModuleState).toArray(SwerveModuleState[]::new);
    }

    /**
     * Get an array representing the converted Output {@link edu.wpi.first.math.kinematics.SwerveModuleState state} of each module in order
     * 
     * @return An array of {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleStates}
     */
    public static SwerveModuleState[] getOutputModuleStates() {
        return (Chassis.IS_SIMULATED)? 
            (MODULES.stream().sequential().map((Module) -> new SwerveModuleState((Module.getTranslationalOutput()), new Rotation2d(Module.getRotationalOutput()))).toArray(SwerveModuleState[]::new)): 
            (MODULES.stream().sequential().map((Module) -> new SwerveModuleState(Module.getTranslationalOutput() * Maximum.ROBOT_MAXIMUM_X_TRANSLATION_OUTPUT,
            new Rotation2d(Module.getRotationalOutput()))).toArray(SwerveModuleState[]::new));
    }

    /**
     * Get the static instance of the Drivebase, if not already created this will also create a new instance.
     *
     * @return Static {@link frc.robot.subsystems.drivebase.DrivebaseSubsystem DrivebaseSubsystem} instance
     */
    public static synchronized DrivebaseSubsystem getInstance() {
        if(java.util.Objects.isNull(INSTANCE)) {
            INSTANCE = new DrivebaseSubsystem();
        }
        return INSTANCE;
    }

    /**
     * Get the {@link edu.wpi.first.math.estimator.SwerveDrivePoseEstimator pose estimator}'s estimated {@link edu.wpi.first.math.geometry.Pose2d Pose2d} based on measurement data
     *
     * @return estimated {@link edu.wpi.first.math.geometry.Pose2d Pose2d} based on measurement data
     */
    public Pose2d get() {
        return POSE_ESTIMATOR.getEstimatedPosition();
    }
}