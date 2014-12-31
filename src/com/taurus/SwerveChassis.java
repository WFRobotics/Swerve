/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.taurus;

import edu.wpi.first.wpilibj.Gyro;
import edu.wpi.first.wpilibj.Servo;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Swerve chassis implementation
 * @author Team 4818 Taurus Robotics
 */
public class SwerveChassis
{
    public boolean FieldRelative = true;
    
    public double MaxAvailableVelocity = 1.0;
    
    private Gyro RobotGyro;
 
    private SwerveWheel[] Wheels;

    // shifter
    private Servo Shifter[] = new Servo[2];
    private final double[] ShifterLevelHigh = {120, 45};// 170 to 0 is max range allowed for the servo 
    private final double[] ShifterLevelLow = {45, 120};
    private int Gear;

    private PIController ChassisAngleController;
    public double ChassisP = 1.0 / 90;  // Full speed rotation at error of 90 degrees. 
    public double ChassisI = 0;
    
    /**
     * sets up individual wheels and their positions relative to robot center
     */
    public SwerveChassis()
    {
        Shifter[0] = new Servo(SwerveConstants.WheelShiftServoPins[0]);
        Shifter[1] = new Servo(SwerveConstants.WheelShiftServoPins[1]);
        
        RobotGyro = new Gyro(SwerveConstants.GyroPin);
        
        ChassisAngleController = new PIController(ChassisP, ChassisI, 1.0);
        
        Wheels = new SwerveWheel[SwerveConstants.WheelCount];
 
        // {x, y}, Orientation, {EncoderA, EncoderB}, Pot, Drive, Angle
        for (int i = 0; i < SwerveConstants.WheelCount; i++)
        {
            Wheels[i] = new SwerveWheel("wheel" + i,
                                        SwerveConstants.WheelPositions[i],
                                        SwerveConstants.WheelOrientationAngle[i],
                                        SwerveConstants.WheelEncoderPins[i],
                                        SwerveConstants.WheelPotPins[i],
                                        SwerveConstants.WheelDriveMotorPins[i],
                                        SwerveConstants.WheelAngleMotorPins[i]);
        }
 
    }
    
    /**
     * Updates the chassis for Angle Drive
     * @param Velocity robot's velocity/movement using SwerveVector type
     * @param Heading robot's heading/angle using SwerveVector type
     * @return Array of SwerveVectors of the actual readings from the wheels
     */
    public SwerveVector[] UpdateAngleDrive(SwerveVector Velocity, double Heading)
    {
        // set the rotation using a PI controller based on current robot heading and new desired heading
        double Error = Utilities.wrapToRange(Heading - RobotGyro.getAngle(), -180, 180);
        double Rotation = ChassisAngleController.update(Error, Timer.getFPGATimestamp());
        
        SmartDashboard.putNumber("AngleDrive.error", Error);
        SmartDashboard.putNumber("AngleDrive.rotation", Rotation);
        
        return UpdateHaloDrive(Velocity, Rotation);
    }
    
    /**
     * Updates the chassis for Halo Drive from SwerveVector type of velocity
     * @param Velocity robot's velocity using SwerveVector type
     * @param Rotation robot's rotational movement, -1 to 1 rad/s
     * @return Array of SwerveVectors of the actual readings from the wheels
     */
    public SwerveVector[] UpdateHaloDrive(SwerveVector Velocity, double Rotation)
    {
        if (FieldRelative)
        {
            Velocity.setAngle(adjustAngleFromGyro(Velocity.getAngle()));
        }
        
        UpdateShifter();
        
        return setWheelVectors(Velocity, Rotation);
    }
    
    /**
     * Scale the wheel vectors based on max available velocity, adjust for
     * rotation rate, then set/update the desired vectors individual wheels
     * @param RobotVelocity robot's velocity using SwerveVector type; max speed is 1.0
     * @param RobotRotation robot's rotational movement; max rotation speed is -1 or 1
     * @return Array of SwerveVectors of the actual readings from the wheels
     */
    private SwerveVector[] setWheelVectors(SwerveVector RobotVelocity, double RobotRotation)
    {
        SwerveVector[] WheelsUnscaled = new SwerveVector[SwerveConstants.WheelCount]; // Unscaled Wheel Velocities
        SwerveVector[] WheelsActual = new SwerveVector[SwerveConstants.WheelCount];   // Actual Wheel Velocities
        
        double MaxWantedVeloc = 0;

        // set limitations on speed
        if (RobotVelocity.getMag() > 1.0)
        {
            RobotVelocity.setMag(1.0);
        }
        
        // set limitations on rotation
        RobotRotation = Utilities.clampToRange(RobotRotation, -1, 1);
        
        // calculate vectors for each wheel
        for (int i = 0; i < SwerveConstants.WheelCount; i++)
        {
            //calculate
            WheelsUnscaled[i] = new SwerveVector(RobotVelocity.getX() - RobotRotation * Wheels[i].getPosition().getY(),
                                                 RobotVelocity.getY() + RobotRotation * Wheels[i].getPosition().getX());

            if (WheelsUnscaled[i].getMag() >= MaxWantedVeloc)
            {
                MaxWantedVeloc = WheelsUnscaled[i].getMag();
            }
        }
        
        double Ratio = MaxAvailableVelocity / MaxWantedVeloc;
        
        if (Ratio > 1)
        {
            Ratio = 1;
        }

        for (int i = 0; i < SwerveConstants.WheelCount; i++)
        {
            // Scale values for each wheel
            SwerveVector WheelScaled = SwerveVector.NewFromMagAngle(
                    WheelsUnscaled[i].getMag() * Ratio,
                    WheelsUnscaled[i].getAngle());

            // Set the wheel speed
            WheelsActual[i] = Wheels[i].setDesired(WheelScaled, this.getGearHigh());
        }
        
        return WheelsActual;
    }
    
    /**
     * Update the Shifting/Gear servo
     */
    public void UpdateShifter()
    {
        // switch to the desired gear
        switch (Gear)
        {
            case SwerveConstants.GearLow:
                Shifter[0].setAngle(ShifterLevelLow[0]);
                Shifter[1].setAngle(ShifterLevelLow[1]);
                break;

            case SwerveConstants.GearHigh:
            default:
                Shifter[0].setAngle(ShifterLevelHigh[0]);
                Shifter[1].setAngle(ShifterLevelHigh[1]);
                break;
        }
    }
    
    /**
     * Adjust the new angle based on the Gyroscope angle
     * @param Angle new desired angle
     * @return adjusted angle
     */
    private double adjustAngleFromGyro(double Angle)
    {
        // adjust the desired angle based on the robot's current angle
        double AdjustedAngle = Angle - RobotGyro.getAngle();
        
        // Wrap to fit in the range -180 to 180
        return Utilities.wrapToRange(AdjustedAngle, -180, 180);
    }
    
    /**
     * Set the shifting gear
     * @param GearHigh if true, shift to high gear, else low gear
     */
    public void setGearHigh(boolean GearHigh)
    {
        // Shift gears if necessary
        if (GearHigh)
        {
            Gear = SwerveConstants.GearHigh;
        }
        else
        {
            Gear = SwerveConstants.GearLow;
        }
    }
    
    /**
     * Get the shifting gear
     * @return true if currently in high gear, else false
     */
    public boolean getGearHigh()
    {
        boolean retVal = false;
        
        if (Gear == SwerveConstants.GearHigh)
        {
            retVal = true;
        }
        
        return retVal;
    }
    
    /**
     * Get the actual reading of a wheel
     * @param index Index of the wheel
     * @return Actual reading of the wheel
     */
    public SwerveVector getWheelActual(int index)
    {
        return Wheels[index].getActual();
    }
    
    /**
     * Get the Gyro object
     * @return Gyro object
     */
    public Gyro getGyro()
    {
    	return RobotGyro;
    }
    
    /**
     * Get the SwerveWheel object for the specified index
     * @param index of wheel to get
     * @return SwerveWheel object
     */
    public SwerveWheel getWheel(int index)
    {
        return Wheels[index];
    }
}